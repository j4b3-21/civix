package com.civix.agent.service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.civix.agent.client.AgentClient;
import com.civix.agent.client.AgentRejectedException;
import com.civix.agent.client.AgentWire.CitizenInputResume;
import com.civix.agent.client.AgentWire.ResumeRequest;
import com.civix.agent.domain.AgentTask;
import com.civix.agent.domain.TaskStatus;
import com.civix.agent.dto.AgentTaskDto;
import com.civix.agent.dto.CitizenInputRequest;
import com.civix.agent.dto.ExecutionEventDto;
import com.civix.agent.repository.AgentTaskRepository;
import com.civix.agent.repository.ExecutionEventRepository;
import com.civix.applications.domain.ApplicationStatus;
import com.civix.applications.repository.ApplicationRepository;
import com.civix.applications.service.ApplicationStateService;
import com.civix.audit.domain.ActorType;
import com.civix.audit.domain.AuditCategory;
import com.civix.audit.service.AuditEntry;
import com.civix.audit.service.AuditService;
import com.civix.common.api.ApiException;
import com.civix.common.storage.ObjectStorageService;
import com.civix.common.storage.ObjectStorageService.StoredObject;

@Service
public class AgentTaskService {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskService.class);

    private final AgentTaskRepository tasks;
    private final ExecutionEventRepository events;
    private final ApplicationRepository applications;
    private final ApplicationStateService state;
    private final AgentTaskViewMapper views;
    private final AgentClient agent;
    private final SseHub hub;
    private final ObjectStorageService storage;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AgentTaskService(AgentTaskRepository tasks, ExecutionEventRepository events, ApplicationRepository applications,
                            ApplicationStateService state, AgentTaskViewMapper views, AgentClient agent, SseHub hub,
                            ObjectStorageService storage, AuditService audit, PlatformTransactionManager txManager, Clock clock) {
        this.tasks = tasks;
        this.events = events;
        this.applications = applications;
        this.state = state;
        this.views = views;
        this.agent = agent;
        this.hub = hub;
        this.storage = storage;
        this.audit = audit;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AgentTaskDto get(UUID userId, UUID taskId) {
        return views.toDto(owned(userId, taskId));
    }

    @Transactional(readOnly = true)
    public Page<AgentTaskDto> list(UUID userId, Pageable pageable) {
        return tasks.findByUserId(userId, pageable).map(views::toDto);
    }

    @Transactional(readOnly = true)
    public AgentTask owned(UUID userId, UUID taskId) {
        return tasks.findByIdAndUserId(taskId, userId).orElseThrow(() -> ApiException.notFound("task"));
    }

    @Transactional(readOnly = true)
    public SseEmitter subscribe(UUID userId, UUID taskId, long lastEventId) {
        owned(userId, taskId);
        return hub.subscribe(taskId, lastEventId);
    }

    @Transactional(readOnly = true)
    public List<ExecutionEventDto> history(UUID taskId, long afterSequence, int limit) {
        int size = Math.max(1, Math.min(limit, 500));
        return events.findByTaskIdAndSequenceGreaterThanOrderBySequenceAsc(taskId, Math.max(0, afterSequence), PageRequest.of(0, size))
                .stream().map(ExecutionEventDto::from).toList();
    }

    @Transactional
    public AgentTaskDto cancel(UUID userId, UUID taskId, String reason) {
        AgentTask t = tasks.findByIdForUpdate(taskId).filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("task"));
        if (t.getStatus().isEnded()) {
            throw ApiException.conflict("TASK_ENDED", "This run has already ended.");
        }
        t.requestCancel(clock.instant());
        try {
            agent.cancel(taskId, reason);
        } catch (RuntimeException e) {
            if (t.getStatus() == TaskStatus.QUEUED) {
                // The agent never picked it up: cancel locally.
                t.changeStatus(TaskStatus.CANCELLED, clock.instant());
                applications.findByIdForUpdate(t.getApplicationId())
                        .filter(a -> a.getStatus() == ApplicationStatus.IN_PROGRESS)
                        .ifPresent(a -> state.transition(a, ApplicationStatus.CANCELLED, "Cancelled before the agent started"));
            } else {
                throw e;
            }
        }
        audit.record(AuditEntry.of(AuditCategory.AGENT, "AGENT_CANCEL_REQUESTED").user(userId).actor(ActorType.USER, userId.toString())
                .resource("AGENT_TASK", taskId).summary("Asked the agent to stop"));
        return views.toDto(t);
    }

    /** Forwards an OTP/CAPTCHA/declaration to the agent. The value is never stored, logged or audited. */
    @Transactional(readOnly = true)
    public void citizenInput(UUID userId, UUID taskId, CitizenInputRequest req) {
        AgentTask t = owned(userId, taskId);
        Map<String, Object> pending = t.getPendingCitizenInput();
        if (t.getStatus() != TaskStatus.WAITING_CITIZEN || pending == null || !req.requestId().equals(String.valueOf(pending.get("requestId")))) {
            throw ApiException.conflict("INPUT_NOT_EXPECTED", "The agent is not waiting for this input.");
        }
        try {
            agent.resume(taskId, ResumeRequest.citizenInput(new CitizenInputResume(req.requestId(), req.value())));
        } catch (AgentRejectedException e) {
            if (e.agentStatus() == 409) {
                throw ApiException.conflict("INPUT_NOT_EXPECTED", "The agent is no longer waiting for this input.");
            }
            throw e;
        }
        audit.recordIndependently(AuditEntry.of(AuditCategory.AGENT, "CITIZEN_INPUT_PROVIDED").user(userId)
                .actor(ActorType.USER, userId.toString()).resource("AGENT_TASK", taskId)
                .summary("Provided " + pending.getOrDefault("kind", "input") + " to the agent (value not stored)"));
    }

    /**
     * Resumes a run that crashed mid-way from its last checkpoint. The status change is committed before the agent is
     * called (it reports back immediately) and reverted if the agent refuses.
     */
    public AgentTaskDto retry(UUID userId, UUID taskId) {
        tx.executeWithoutResult(status -> {
            AgentTask t = tasks.findByIdForUpdate(taskId).filter(x -> x.getUserId().equals(userId))
                    .orElseThrow(() -> ApiException.notFound("task"));
            if (t.getStatus() != TaskStatus.FAILED || !Boolean.TRUE.equals(t.getErrorRetryable())) {
                throw notResumable();
            }
            t.resetForRetry(clock.instant());
            applications.findByIdForUpdate(t.getApplicationId())
                    .filter(a -> a.getStatus() == ApplicationStatus.FAILED)
                    .ifPresent(a -> state.transition(a, ApplicationStatus.IN_PROGRESS, "Agent run retried"));
        });
        try {
            agent.resume(taskId, ResumeRequest.retry());
        } catch (RuntimeException e) {
            boolean refused = e instanceof AgentRejectedException r && r.agentStatus() == 409;
            tx.executeWithoutResult(status -> tasks.findByIdForUpdate(taskId).ifPresent(t -> {
                if (refused) {
                    t.fail("TASK_NOT_RESUMABLE", "This run cannot continue. Start a new run for the application.", false, clock.instant());
                } else {
                    t.fail("AGENT_UNAVAILABLE", "The automation service could not be reached. Try again shortly.", true, clock.instant());
                }
                applications.findByIdForUpdate(t.getApplicationId())
                        .filter(a -> a.getStatus() == ApplicationStatus.IN_PROGRESS)
                        .ifPresent(a -> state.transition(a, ApplicationStatus.FAILED, "Retry refused"));
            }));
            if (refused) {
                throw notResumable();
            }
            log.warn("Retry of task {} failed: {}", taskId, e.getClass().getSimpleName());
            throw e;
        }
        return tx.execute(status -> views.toDto(tasks.findById(taskId).orElseThrow()));
    }

    private static ApiException notResumable() {
        return new ApiException("TASK_NOT_RESUMABLE", HttpStatus.CONFLICT,
                "This run cannot be resumed. Start a new run for the application.");
    }

    @Transactional(readOnly = true)
    public StoredObject screenshot(UUID userId, UUID taskId, UUID screenshotId) {
        owned(userId, taskId);
        return storage.get("tasks/" + taskId + "/screenshots/" + screenshotId + ".png")
                .orElseThrow(() -> ApiException.notFound("screenshot"));
    }
}
