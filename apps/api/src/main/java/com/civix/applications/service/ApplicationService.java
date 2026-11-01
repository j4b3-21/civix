package com.civix.applications.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.civix.agent.client.AgentClient;
import com.civix.agent.client.AgentUnavailableException;
import com.civix.agent.client.AgentWire.CreateTaskRequest;
import com.civix.agent.client.AgentWire.Intent;
import com.civix.agent.client.AgentWire.TaskDocument;
import com.civix.agent.domain.AgentTask;
import com.civix.agent.domain.TaskStatus;
import com.civix.agent.dto.AgentTaskDto;
import com.civix.agent.repository.AgentTaskRepository;
import com.civix.agent.service.AgentTaskViewMapper;
import com.civix.applications.domain.Application;
import com.civix.applications.domain.ApplicationStatus;
import com.civix.applications.dto.ApplicationDto;
import com.civix.applications.repository.ApplicationRepository;
import com.civix.approvals.service.ApprovalService;
import com.civix.audit.domain.ActorType;
import com.civix.audit.domain.AuditCategory;
import com.civix.audit.service.AuditEntry;
import com.civix.audit.service.AuditService;
import com.civix.citizen.dto.CitizenContext;
import com.civix.citizen.service.CitizenContextService;
import com.civix.common.api.ApiException;
import com.civix.consent.domain.ConsentPurpose;
import com.civix.consent.service.ConsentService;
import com.civix.documents.domain.Document;
import com.civix.documents.domain.ReviewStatus;
import com.civix.documents.repository.DocumentRepository;
import com.civix.eligibility.domain.EligibilityOutcome;
import com.civix.eligibility.repository.EligibilityEvaluationRepository;
import com.civix.services.domain.AutomationStatus;
import com.civix.services.domain.ServiceDefinition;
import com.civix.services.repository.ServiceDefinitionRepository;
import com.civix.services.service.ServiceCatalogService;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final ApplicationRepository applications;
    private final ServiceCatalogService catalog;
    private final ServiceDefinitionRepository services;
    private final EligibilityEvaluationRepository evaluations;
    private final AgentTaskRepository tasks;
    private final DocumentRepository documents;
    private final CitizenContextService contexts;
    private final ConsentService consents;
    private final ApplicationStateService state;
    private final ApprovalService approvals;
    private final ApplicationViewMapper views;
    private final AgentTaskViewMapper taskViews;
    private final AgentClient agent;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ApplicationService(ApplicationRepository applications, ServiceCatalogService catalog,
                              ServiceDefinitionRepository services, EligibilityEvaluationRepository evaluations,
                              AgentTaskRepository tasks, DocumentRepository documents, CitizenContextService contexts,
                              ConsentService consents, ApplicationStateService state, ApprovalService approvals,
                              ApplicationViewMapper views, AgentTaskViewMapper taskViews, AgentClient agent,
                              AuditService audit, PlatformTransactionManager txManager, Clock clock) {
        this.applications = applications;
        this.catalog = catalog;
        this.services = services;
        this.evaluations = evaluations;
        this.tasks = tasks;
        this.documents = documents;
        this.contexts = contexts;
        this.consents = consents;
        this.state = state;
        this.approvals = approvals;
        this.views = views;
        this.taskViews = taskViews;
        this.agent = agent;
        this.audit = audit;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    @Transactional
    public ApplicationDto create(UUID userId, UUID serviceId) {
        ServiceDefinition s = catalog.visible(serviceId);
        if (applications.existsByUserIdAndServiceIdAndStatusIn(userId, s.getId(), ApplicationStatus.OPEN)) {
            throw ApiException.conflict("APPLICATION_EXISTS", "You already have an open application for this service.");
        }
        EligibilityOutcome outcome = evaluations.findFirstByUserIdAndServiceIdOrderByEvaluatedAtDesc(userId, s.getId())
                .map(e -> e.getOutcome()).orElse(null);
        Application app = new Application(userId, s.getId(), s.getCode(), s.getDefinitionVersion(), outcome, s.isTestService(), clock.instant());
        try {
            applications.saveAndFlush(app);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("APPLICATION_EXISTS", "You already have an open application for this service.");
        }
        audit.record(AuditEntry.of(AuditCategory.APPLICATION, "APPLICATION_CREATED").user(userId)
                .actor(ActorType.USER, userId.toString()).resource("APPLICATION", app.getId())
                .summary("Started an application for " + s.getName()));
        return views.toDto(app);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationDto> list(UUID userId, ApplicationStatus status, Pageable pageable) {
        Page<Application> page = status == null ? applications.findByUserId(userId, pageable)
                : applications.findByUserIdAndStatus(userId, status, pageable);
        return page.map(views::toDto);
    }

    @Transactional(readOnly = true)
    public ApplicationDto get(UUID userId, UUID applicationId) {
        return views.toDto(owned(userId, applicationId));
    }

    @Transactional(readOnly = true)
    public Application owned(UUID userId, UUID applicationId) {
        return applications.findByIdAndUserId(applicationId, userId).orElseThrow(() -> ApiException.notFound("application"));
    }

    @Transactional
    public ApplicationDto cancel(UUID userId, UUID applicationId) {
        Application app = applications.findOwnedForUpdate(applicationId, userId).orElseThrow(() -> ApiException.notFound("application"));
        tasks.findFirstByApplicationIdAndStatusIn(app.getId(), TaskStatus.ACTIVE).ifPresent(t -> {
            t.requestCancel(clock.instant());
            try {
                agent.cancel(t.getId(), "APPLICATION_CANCELLED");
            } catch (RuntimeException e) {
                log.warn("Agent cancel for task {} failed: {}", t.getId(), e.getClass().getSimpleName());
            }
            approvals.invalidateForTask(t.getId(), "APPLICATION_CANCELLED");
        });
        state.transition(app, ApplicationStatus.CANCELLED, "Cancelled by citizen");
        app.setRequiredAction(null, clock.instant());
        audit.record(AuditEntry.of(AuditCategory.APPLICATION, "APPLICATION_CANCELLED").user(userId)
                .actor(ActorType.USER, userId.toString()).resource("APPLICATION", app.getId()).summary("Cancelled an application"));
        return views.toDto(app);
    }

    /**
     * Starts an agent run. The task row is committed first (the agent calls back immediately), then the agent is
     * asked to start; if it cannot be reached the task is marked FAILED (retryable) and 503 is returned.
     */
    public AgentTaskDto execute(UUID userId, UUID applicationId) {
        Prepared prepared = tx.execute(status -> prepare(userId, applicationId));
        try {
            agent.createTask(prepared.request());
        } catch (RuntimeException e) {
            tx.executeWithoutResult(status -> tasks.findByIdForUpdate(prepared.taskId()).ifPresent(t -> {
                t.fail("AGENT_UNAVAILABLE", "The automation service could not be reached. Try again shortly.", true, clock.instant());
                applications.findByIdForUpdate(t.getApplicationId()).ifPresent(a -> {
                    if (a.getStatus() == ApplicationStatus.IN_PROGRESS) {
                        state.transition(a, ApplicationStatus.FAILED, "Agent unavailable");
                    }
                });
            }));
            if (e instanceof ApiException api) {
                throw api;
            }
            throw new AgentUnavailableException("The automation service is not reachable right now. Please try again shortly.", e);
        }
        return tx.execute(status -> taskViews.toDto(tasks.findById(prepared.taskId()).orElseThrow()));
    }

    private record Prepared(UUID taskId, CreateTaskRequest request) {
    }

    private Prepared prepare(UUID userId, UUID applicationId) {
        Application app = applications.findOwnedForUpdate(applicationId, userId).orElseThrow(() -> ApiException.notFound("application"));
        consents.require(userId, ConsentPurpose.AGENT_EXECUTION);
        ServiceDefinition s = services.findById(app.getServiceId()).orElseThrow(() -> ApiException.notFound("service"));
        boolean executable = s.isActive() && (s.getAutomationStatus() == AutomationStatus.ACTIVE
                || (s.getAutomationStatus() == AutomationStatus.TEST_ONLY && s.isTestService() && catalog.testServicesEnabled()));
        if (!executable) {
            throw new ApiException("AUTOMATION_UNAVAILABLE", HttpStatus.UNPROCESSABLE_ENTITY,
                    "CIVIX cannot run this service on the portal. Use the checklist and apply on the official portal.");
        }
        if (tasks.findFirstByApplicationIdAndStatusIn(app.getId(), TaskStatus.ACTIVE).isPresent()) {
            throw ApiException.conflict("TASK_ACTIVE", "An agent run is already active for this application.");
        }
        if (!(app.getStatus() == ApplicationStatus.DRAFT || app.getStatus() == ApplicationStatus.ACTION_REQUIRED
                || app.getStatus() == ApplicationStatus.FAILED)) {
            throw ApiException.conflict("APPLICATION_NOT_RUNNABLE", "This application cannot be run in its current state (" + app.getStatus() + ").");
        }
        Instant now = clock.instant();
        AgentTask task = new AgentTask(app.getId(), userId, now);
        tasks.saveAndFlush(task);
        state.transition(app, ApplicationStatus.IN_PROGRESS, "Agent run started");
        app.setRequiredAction(null, now);

        CitizenContext context = contexts.build(userId, ConsentPurpose.AGENT_EXECUTION);
        List<TaskDocument> docs = documents.findByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(userId).stream()
                .map(this::taskDocument).toList();
        audit.record(AuditEntry.of(AuditCategory.AGENT, "AGENT_RUN_STARTED").user(userId).actor(ActorType.USER, userId.toString())
                .resource("AGENT_TASK", task.getId()).summary("Started an agent run for " + s.getName())
                .meta("applicationId", app.getId().toString()).meta("documents", docs.size()));
        CreateTaskRequest request = new CreateTaskRequest(task.getId(), app.getId(), userId, s.getCode(), new Intent("APPLY", null),
                context, docs, app.getDataVersion());
        return new Prepared(task.getId(), request);
    }

    private TaskDocument taskDocument(Document d) {
        return new TaskDocument(d.getId(), d.getCategory().name(), d.getStorageKey(), d.getMimeType(), d.getSha256(),
                d.getFileName(), d.getReviewStatus() == ReviewStatus.REVIEWED);
    }
}
