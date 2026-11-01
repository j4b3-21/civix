package com.civix.dashboard.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civix.agent.domain.TaskStatus;
import com.civix.agent.dto.AgentTaskDto;
import com.civix.agent.repository.AgentTaskRepository;
import com.civix.agent.service.AgentTaskViewMapper;
import com.civix.applications.domain.ApplicationStatus;
import com.civix.applications.dto.ApplicationDto;
import com.civix.applications.repository.ApplicationRepository;
import com.civix.applications.service.ApplicationNames;
import com.civix.applications.service.ApplicationViewMapper;
import com.civix.approvals.domain.ApprovalStatus;
import com.civix.approvals.dto.ApprovalDto;
import com.civix.approvals.repository.ApprovalRepository;
import com.civix.audit.dto.AuditEventDto;
import com.civix.audit.service.AuditService;
import com.civix.citizen.dto.CompletenessDto;
import com.civix.citizen.service.ProfileService;
import com.civix.eligibility.domain.EligibilityEvaluation;
import com.civix.eligibility.domain.EligibilityOutcome;
import com.civix.eligibility.repository.EligibilityEvaluationRepository;
import com.civix.services.dto.ServiceSummaryDto;
import com.civix.services.service.ServiceCatalogService;
import com.civix.services.service.ServiceViewMapper;

/** Aggregates the citizen's dashboard from real records only (no placeholder figures). */
@Service
public class DashboardService {

    private static final Set<EligibilityOutcome> RECOMMENDABLE =
            EnumSet.of(EligibilityOutcome.ELIGIBLE, EligibilityOutcome.UNVERIFIED, EligibilityOutcome.NEEDS_MORE_INFORMATION);

    public record DashboardDto(CompletenessDto profileCompleteness, List<ApplicationDto> activeApplications,
                               List<ApprovalDto> pendingApprovals, List<AgentTaskDto> pendingCitizenInputs,
                               List<AuditEventDto> recentActivity, List<ServiceSummaryDto> recommended,
                               List<ServiceSummaryDto> upcomingDeadlines) {
    }

    private final ProfileService profiles;
    private final ApplicationRepository applications;
    private final ApplicationViewMapper applicationViews;
    private final ApprovalRepository approvals;
    private final ApplicationNames names;
    private final AgentTaskRepository tasks;
    private final AgentTaskViewMapper taskViews;
    private final AuditService audit;
    private final EligibilityEvaluationRepository evaluations;
    private final ServiceCatalogService catalog;
    private final ServiceViewMapper serviceViews;
    private final Clock clock;

    public DashboardService(ProfileService profiles, ApplicationRepository applications, ApplicationViewMapper applicationViews,
                            ApprovalRepository approvals, ApplicationNames names, AgentTaskRepository tasks,
                            AgentTaskViewMapper taskViews, AuditService audit, EligibilityEvaluationRepository evaluations,
                            ServiceCatalogService catalog, ServiceViewMapper serviceViews, Clock clock) {
        this.profiles = profiles;
        this.applications = applications;
        this.applicationViews = applicationViews;
        this.approvals = approvals;
        this.names = names;
        this.tasks = tasks;
        this.taskViews = taskViews;
        this.audit = audit;
        this.evaluations = evaluations;
        this.catalog = catalog;
        this.serviceViews = serviceViews;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardDto forUser(UUID userId) {
        List<ApplicationDto> active = applications
                .findByUserIdAndStatusInOrderByLastActivityAtDesc(userId, ApplicationStatus.OPEN, PageRequest.of(0, 10))
                .stream().map(applicationViews::toDto).toList();
        List<ApprovalDto> pending = approvals
                .findByUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(userId, ApprovalStatus.PENDING, clock.instant(), PageRequest.of(0, 10))
                .stream().map(a -> ApprovalDto.from(a, names.serviceName(a.getApplicationId()))).toList();
        List<AgentTaskDto> inputs = tasks
                .findByUserIdAndStatusOrderByUpdatedAtDesc(userId, TaskStatus.WAITING_CITIZEN, PageRequest.of(0, 10))
                .stream().map(taskViews::toDto).toList();
        List<AuditEventDto> activity = audit
                .forUser(userId, null, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "occurredAt"))).getContent();
        List<UUID> recommendedIds = evaluations.findLatestPerService(userId).stream()
                .filter(e -> RECOMMENDABLE.contains(e.getOutcome()))
                .map(EligibilityEvaluation::getServiceId).distinct().limit(6).toList();
        List<ServiceSummaryDto> recommended = recommendedIds.isEmpty() ? List.of()
                : serviceViews.summaries(catalog.visibleByIds(recommendedIds), userId);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
        List<ServiceSummaryDto> deadlines = serviceViews.summaries(catalog.withUpcomingVerifiedDeadline(today, 5), userId);
        return new DashboardDto(profiles.completeness(userId), active, pending, inputs, activity, recommended, deadlines);
    }
}
