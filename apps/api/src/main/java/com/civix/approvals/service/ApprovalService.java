package com.civix.approvals.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civix.agent.client.AgentClient;
import com.civix.agent.client.AgentRejectedException;
import com.civix.agent.client.AgentWire.ApprovalResume;
import com.civix.agent.client.AgentWire.ResumeRequest;
import com.civix.agent.domain.AgentTask;
import com.civix.agent.repository.AgentTaskRepository;
import com.civix.applications.domain.Application;
import com.civix.applications.repository.ApplicationRepository;
import com.civix.applications.service.ApplicationNames;
import com.civix.approvals.domain.ActionClass;
import com.civix.approvals.domain.Approval;
import com.civix.approvals.domain.ApprovalDecision;
import com.civix.approvals.domain.ApprovalKind;
import com.civix.approvals.domain.ApprovalStatus;
import com.civix.approvals.dto.ApprovalDto;
import com.civix.approvals.dto.DecisionRequest;
import com.civix.approvals.dto.InternalApprovalRequest;
import com.civix.approvals.dto.InternalApprovalResponse;
import com.civix.approvals.repository.ApprovalRepository;
import com.civix.audit.domain.ActorType;
import com.civix.audit.domain.AuditCategory;
import com.civix.audit.domain.AuditOutcome;
import com.civix.audit.service.AuditEntry;
import com.civix.audit.service.AuditService;
import com.civix.common.api.ApiException;
import com.civix.common.api.StateChangingApiException;
import com.civix.notifications.domain.NotificationType;
import com.civix.notifications.service.NotificationService;

/**
 * Human approval and trust layer (docs/WORKFLOWS.md §4). Approvals are bound to user, application, task, kind,
 * payload digest, data version and expiry. Authorisation is enforced here, again in the agent (token verification)
 * and again in the browser worker before a submit click. The token is never returned to the browser.
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);
    private static final Collection<ApprovalStatus> OUTSTANDING = EnumSet.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED);

    private final ApprovalRepository approvals;
    private final AgentTaskRepository tasks;
    private final ApplicationRepository applications;
    private final ApplicationNames names;
    private final ApprovalTokenSigner signer;
    private final AgentClient agent;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    public ApprovalService(ApprovalRepository approvals, AgentTaskRepository tasks, ApplicationRepository applications,
                           ApplicationNames names, ApprovalTokenSigner signer, AgentClient agent,
                           NotificationService notifications, AuditService audit, Clock clock) {
        this.approvals = approvals;
        this.tasks = tasks;
        this.applications = applications;
        this.names = names;
        this.signer = signer;
        this.agent = agent;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- agent side

    @Transactional
    public InternalApprovalResponse createFromAgent(InternalApprovalRequest req) {
        AgentTask task = tasks.findByIdForUpdate(req.taskId()).orElseThrow(() -> ApiException.notFound("task"));
        if (!task.getApplicationId().equals(req.applicationId())) {
            throw new ApiException("TASK_APPLICATION_MISMATCH", HttpStatus.UNPROCESSABLE_ENTITY,
                    "The task does not belong to this application.");
        }
        if (task.getStatus().isEnded()) {
            throw ApiException.conflict("TASK_ENDED", "The task has already ended.");
        }
        if (req.kind() == ApprovalKind.FINAL_SUBMISSION && req.actionClass() != ActionClass.CONSEQUENTIAL) {
            throw new ApiException("INVALID_ACTION_CLASS", HttpStatus.UNPROCESSABLE_ENTITY,
                    "Final submission approvals must be CONSEQUENTIAL.");
        }
        Application app = applications.findById(req.applicationId()).orElseThrow(() -> ApiException.notFound("application"));
        if (app.getDataVersion() != req.dataVersion()) {
            throw ApiException.conflict("DATA_CHANGED", "The application data changed; the agent must re-plan.");
        }
        Instant now = clock.instant();
        for (Approval previous : approvals.findByTaskIdAndKindAndStatus(task.getId(), req.kind(), ApprovalStatus.PENDING)) {
            previous.invalidate("SUPERSEDED");
        }
        approvals.flush();
        Approval a = new Approval(task.getId(), app.getId(), task.getUserId(), req.kind(), req.actionClass(), req.summary(),
                req.details() == null ? Map.of() : req.details(), req.payloadDigest(), req.dataVersion(), now,
                now.plusSeconds(req.expiresInSeconds()));
        approvals.save(a);
        boolean finalSubmission = req.kind() == ApprovalKind.FINAL_SUBMISSION;
        notifications.notify(task.getUserId(),
                finalSubmission ? NotificationType.SUBMISSION_APPROVAL_REQUIRED : NotificationType.APPROVAL_REQUIRED,
                finalSubmission ? "Approve final submission" : "CIVIX needs your approval",
                req.summary(), "/agent/" + task.getId());
        audit.record(AuditEntry.of(AuditCategory.APPROVAL, "APPROVAL_REQUESTED").user(task.getUserId())
                .actor(ActorType.AGENT, task.getId().toString()).resource("APPROVAL", a.getId())
                .summary("Approval requested: " + req.summary()).meta("kind", req.kind().name()));
        return new InternalApprovalResponse(a.getId(), a.getStatus().name(), a.getExpiresAt());
    }

    /** Marks an APPROVED approval as used (reported by the agent after the gated action ran). */
    @Transactional
    public void consume(UUID taskId, UUID approvalId) {
        approvals.findByIdAndTaskId(approvalId, taskId).ifPresent(a -> {
            if (a.getStatus() == ApprovalStatus.APPROVED) {
                a.consume(clock.instant());
            }
        });
    }

    @Transactional(readOnly = true)
    public Optional<ApprovalDto> pendingForTask(UUID taskId) {
        Instant now = clock.instant();
        return approvals.findFirstByTaskIdAndStatusOrderByCreatedAtDesc(taskId, ApprovalStatus.PENDING)
                .filter(a -> !a.isExpired(now))
                .map(a -> ApprovalDto.from(a, names.serviceName(a.getApplicationId())));
    }

    /** Invalidates every pending or approved-but-unused approval of these applications. */
    @Transactional
    public int invalidateOutstanding(UUID userId, Collection<UUID> applicationIds, String reason) {
        if (applicationIds.isEmpty()) {
            return 0;
        }
        List<Approval> outstanding = approvals.findOutstandingForApplications(userId, applicationIds, OUTSTANDING);
        for (Approval a : outstanding) {
            a.invalidate(reason);
        }
        if (!outstanding.isEmpty()) {
            audit.record(AuditEntry.of(AuditCategory.APPROVAL, "APPROVALS_INVALIDATED").user(userId)
                    .actor(ActorType.SYSTEM, "civix").summary(outstanding.size() + " approval(s) invalidated: " + reason)
                    .meta("reason", reason));
        }
        return outstanding.size();
    }

    @Transactional
    public void invalidateForTask(UUID taskId, String reason) {
        for (Approval a : approvals.findByTaskIdAndStatusIn(taskId, OUTSTANDING)) {
            a.invalidate(reason);
        }
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    @Transactional
    public void expireOverdue() {
        int n = approvals.expirePending(clock.instant());
        if (n > 0) {
            log.info("Expired {} pending approval(s)", n);
        }
    }

    // ---------------------------------------------------------------- citizen side

    @Transactional(readOnly = true)
    public Page<ApprovalDto> list(UUID userId, ApprovalStatus status, Pageable pageable) {
        Page<Approval> page = status == null ? approvals.findByUserId(userId, pageable)
                : approvals.findByUserIdAndStatus(userId, status, pageable);
        return page.map(a -> ApprovalDto.from(a, names.serviceName(a.getApplicationId())));
    }

    @Transactional(readOnly = true)
    public ApprovalDto get(UUID userId, UUID approvalId) {
        Approval a = approvals.findByIdAndUserId(approvalId, userId).orElseThrow(() -> ApiException.notFound("approval"));
        return ApprovalDto.from(a, names.serviceName(a.getApplicationId()));
    }

    /**
     * Records the citizen's decision and hands it to the agent. The agent call happens inside the transaction: if the
     * agent cannot be reached the decision is rolled back and the citizen can try again, so an approval is never
     * recorded as given without the agent knowing.
     */
    @Transactional(noRollbackFor = StateChangingApiException.class)
    public ApprovalDto decide(UUID userId, UUID approvalId, DecisionRequest req) {
        Approval a = approvals.findOwnedForUpdate(approvalId, userId).orElseThrow(() -> ApiException.notFound("approval"));
        Instant now = clock.instant();
        if (a.getStatus() != ApprovalStatus.PENDING) {
            throw ApiException.conflict("APPROVAL_NOT_PENDING", "This approval was already decided or withdrawn.");
        }
        if (a.isExpired(now)) {
            a.expire();
            throw new StateChangingApiException("APPROVAL_EXPIRED", HttpStatus.GONE, "This approval request has expired.");
        }
        Application app = applications.findById(a.getApplicationId()).orElseThrow(() -> ApiException.notFound("application"));
        if (req.dataVersion() != a.getDataVersion() || app.getDataVersion() != a.getDataVersion()) {
            a.invalidate("DATA_CHANGED");
            throw new StateChangingApiException("APPROVAL_DATA_CHANGED", HttpStatus.CONFLICT,
                    "Your information changed after this request was prepared, so it was withdrawn.");
        }
        String reason = req.reason() == null || req.reason().isBlank() ? null : req.reason().strip();
        ApprovalResume resume;
        if (req.decision() == ApprovalDecision.APPROVE) {
            a.approve(reason, now);
            String token = signer.sign(a.getId(), a.getTaskId(), a.getApplicationId(), a.getUserId(), a.getKind().name(),
                    a.getPayloadDigest(), a.getExpiresAt().getEpochSecond());
            resume = new ApprovalResume(a.getId(), a.getKind().name(), "APPROVE", token, a.getPayloadDigest(), a.getExpiresAt());
        } else {
            a.reject(reason, now);
            resume = new ApprovalResume(a.getId(), a.getKind().name(), "REJECT", null, a.getPayloadDigest(), a.getExpiresAt());
        }
        try {
            agent.resume(a.getTaskId(), ResumeRequest.approval(resume));
        } catch (AgentRejectedException e) {
            if (e.agentStatus() == 409) {
                a.invalidate("AGENT_NOT_WAITING");
                throw new StateChangingApiException("APPROVAL_NOT_PENDING", HttpStatus.CONFLICT,
                        "The agent is no longer waiting for this approval.");
            }
            throw e;
        }
        audit.record(AuditEntry.of(AuditCategory.APPROVAL, req.decision() == ApprovalDecision.APPROVE ? "APPROVAL_GRANTED" : "APPROVAL_REJECTED")
                .user(userId).actor(ActorType.USER, userId.toString()).resource("APPROVAL", a.getId())
                .outcome(AuditOutcome.SUCCESS).summary((req.decision() == ApprovalDecision.APPROVE ? "Approved: " : "Rejected: ") + a.getSummary())
                .meta("kind", a.getKind().name()));
        return ApprovalDto.from(a, names.serviceName(a.getApplicationId()));
    }
}
