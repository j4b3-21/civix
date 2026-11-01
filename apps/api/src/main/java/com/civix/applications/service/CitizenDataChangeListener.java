package com.civix.applications.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.civix.agent.client.AgentClient;
import com.civix.agent.domain.AgentTask;
import com.civix.agent.domain.TaskStatus;
import com.civix.agent.repository.AgentTaskRepository;
import com.civix.applications.domain.Application;
import com.civix.applications.domain.ApplicationStatus;
import com.civix.applications.repository.ApplicationRepository;
import com.civix.approvals.service.ApprovalService;
import com.civix.citizen.service.CitizenDataChangedEvent;
import com.civix.consent.domain.ConsentPurpose;
import com.civix.consent.service.ConsentChangedEvent;
import com.civix.notifications.domain.NotificationType;
import com.civix.notifications.service.NotificationService;

/**
 * Keeps approvals honest when the citizen's data or permissions change (docs/WORKFLOWS.md §4):
 * profile/document changes bump the data version of open applications and invalidate outstanding approvals;
 * revoking agent permissions additionally asks the agent to stop active runs.
 * Runs synchronously inside the publisher's transaction.
 */
@Component
public class CitizenDataChangeListener {

    private static final Logger log = LoggerFactory.getLogger(CitizenDataChangeListener.class);

    private final ApplicationRepository applications;
    private final AgentTaskRepository tasks;
    private final ApprovalService approvals;
    private final NotificationService notifications;
    private final AgentClient agent;
    private final Clock clock;

    public CitizenDataChangeListener(ApplicationRepository applications, AgentTaskRepository tasks, ApprovalService approvals,
                                     NotificationService notifications, AgentClient agent, Clock clock) {
        this.applications = applications;
        this.tasks = tasks;
        this.approvals = approvals;
        this.notifications = notifications;
        this.agent = agent;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void onDataChanged(CitizenDataChangedEvent event) {
        List<UUID> open = applications.findIdsByUserAndStatus(event.userId(), ApplicationStatus.OPEN);
        if (open.isEmpty()) {
            return;
        }
        for (UUID id : open) {
            applications.findByIdForUpdate(id).ifPresent((Application a) -> a.bumpDataVersion(clock.instant()));
        }
        int invalidated = approvals.invalidateOutstanding(event.userId(), open, "DATA_CHANGED");
        if (invalidated > 0) {
            notifications.notify(event.userId(), NotificationType.APPROVAL_INVALIDATED, "Approval withdrawn",
                    "Your information changed, so " + invalidated + " pending approval(s) were withdrawn. Run the application again to review the new data.",
                    "/applications");
        }
    }

    @EventListener
    @Transactional
    public void onConsentChanged(ConsentChangedEvent event) {
        if (event.granted() || (event.purpose() != ConsentPurpose.AGENT_EXECUTION && event.purpose() != ConsentPurpose.PORTAL_DOCUMENT_UPLOAD)) {
            return;
        }
        List<UUID> open = applications.findIdsByUserAndStatus(event.userId(), ApplicationStatus.OPEN);
        approvals.invalidateOutstanding(event.userId(), open, "CONSENT_REVOKED");
        for (AgentTask t : tasks.findByUserIdAndStatusIn(event.userId(), TaskStatus.ACTIVE)) {
            t.requestCancel(clock.instant());
            try {
                agent.cancel(t.getId(), "CONSENT_REVOKED");
            } catch (RuntimeException e) {
                log.warn("Could not ask the agent to cancel task {} after consent revocation: {}", t.getId(), e.getClass().getSimpleName());
            }
        }
    }
}
