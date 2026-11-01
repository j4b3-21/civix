package com.civix.agent.service;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civix.agent.domain.AgentCheckpoint;
import com.civix.agent.domain.AgentTask;
import com.civix.agent.domain.EventType;
import com.civix.agent.domain.ExecutionEvent;
import com.civix.agent.domain.TaskStatus;
import com.civix.agent.dto.ExecutionEventDto;
import com.civix.agent.repository.AgentCheckpointRepository;
import com.civix.agent.repository.AgentTaskRepository;
import com.civix.agent.repository.ExecutionEventRepository;
import com.civix.applications.domain.Application;
import com.civix.applications.domain.ApplicationStatus;
import com.civix.applications.domain.TransitionPolicy;
import com.civix.applications.repository.ApplicationRepository;
import com.civix.applications.service.ApplicationStateService;
import com.civix.approvals.repository.ApprovalRepository;
import com.civix.approvals.service.ApprovalService;
import com.civix.audit.domain.ActorType;
import com.civix.audit.domain.AuditCategory;
import com.civix.audit.domain.AuditOutcome;
import com.civix.audit.service.AuditEntry;
import com.civix.audit.service.AuditService;
import com.civix.common.api.ApiException;
import com.civix.documents.domain.AccessAction;
import com.civix.documents.domain.AccessActorType;
import com.civix.documents.service.DocumentAccessLog;
import com.civix.notifications.domain.NotificationType;
import com.civix.notifications.service.NotificationService;

/**
 * Ingests agent events (docs/AGENT_CONTRACTS.md §2.3). Events are facts and are always recorded (idempotent on
 * eventId); the status changes they request are applied only when allowed by docs/WORKFLOWS.md — an illegal
 * transition is ignored, logged and reported back, never applied.
 */
@Service
public class AgentEventIngestService {

    private static final Logger log = LoggerFactory.getLogger(AgentEventIngestService.class);
    private static final Set<String> KNOWN_TYPES;
    private static final Set<String> TERMINAL = Set.of("TASK_COMPLETED", "TASK_FAILED", "TASK_CANCELLED");

    static {
        java.util.HashSet<String> s = new java.util.HashSet<>();
        for (EventType t : EventType.values()) {
            s.add(t.name());
        }
        KNOWN_TYPES = Set.copyOf(s);
    }

    private final ExecutionEventRepository events;
    private final AgentTaskRepository tasks;
    private final ApplicationRepository applications;
    private final ApplicationStateService state;
    private final ApprovalService approvalService;
    private final ApprovalRepository approvals;
    private final AgentCheckpointRepository checkpoints;
    private final DocumentAccessLog accessLog;
    private final NotificationService notifications;
    private final AuditService audit;
    private final SseHub hub;
    private final Clock clock;

    public AgentEventIngestService(ExecutionEventRepository events, AgentTaskRepository tasks, ApplicationRepository applications,
                                   ApplicationStateService state, ApprovalService approvalService, ApprovalRepository approvals,
                                   AgentCheckpointRepository checkpoints, DocumentAccessLog accessLog,
                                   NotificationService notifications, AuditService audit, SseHub hub, Clock clock) {
        this.events = events;
        this.tasks = tasks;
        this.applications = applications;
        this.state = state;
        this.approvalService = approvalService;
        this.approvals = approvals;
        this.checkpoints = checkpoints;
        this.accessLog = accessLog;
        this.notifications = notifications;
        this.audit = audit;
        this.hub = hub;
        this.clock = clock;
    }

    public record IngestResult(int accepted, int duplicates, int rejectedTransitions) {
    }

    @Transactional
    public IngestResult ingest(List<Map<String, Object>> batch) {
        int accepted = 0;
        int duplicates = 0;
        int rejected = 0;
        Map<UUID, AgentTask> taskCache = new HashMap<>();
        Map<UUID, Application> appCache = new HashMap<>();
        for (Map<String, Object> raw : batch) {
            UUID eventId = uuid(raw.get("eventId"), "eventId");
            UUID taskId = uuid(raw.get("taskId"), "taskId");
            String type = string(raw.get("type"));
            if (type == null || !KNOWN_TYPES.contains(type)) {
                throw new ApiException("UNKNOWN_EVENT_TYPE", HttpStatus.UNPROCESSABLE_ENTITY, "Unknown event type.");
            }
            if (events.existsByEventId(eventId)) {
                duplicates++;
                continue;
            }
            AgentTask task = taskCache.computeIfAbsent(taskId,
                    id -> tasks.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("task")));
            Application app = appCache.computeIfAbsent(task.getApplicationId(),
                    id -> applications.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("application")));
            Instant now = clock.instant();
            Map<String, Object> metadata = map(raw.get("metadata"));
            String agentName = truncate(defaultIfBlank(string(raw.get("agent")), "ORCHESTRATOR"), 32);
            ExecutionEvent saved = events.save(new ExecutionEvent(eventId, taskId, type, agentName,
                    truncate(string(raw.get("step")), 128), truncate(defaultIfBlank(string(raw.get("message")), type), 1000),
                    metadata, instant(raw.get("timestamp"), now), now));
            task.recordEvent(saved.getSequence(), now);
            accepted++;

            // ---- task status
            TaskStatus taskStatus = enumOrNull(TaskStatus.class, raw.get("taskStatus"));
            if (taskStatus != null) {
                if (TransitionPolicy.canTransition(task.getStatus(), taskStatus)) {
                    task.changeStatus(taskStatus, now);
                } else {
                    rejected++;
                    log.warn("Ignored illegal task transition {} -> {} for task {} (event {})", task.getStatus(), taskStatus, taskId, type);
                    taskStatus = null;
                }
            }
            task.setProgress(string(raw.get("currentStep")), string(raw.get("currentAgent")));
            if (raw.containsKey("pendingCitizenInput")) {
                Object p = raw.get("pendingCitizenInput");
                task.setPendingCitizenInput(p instanceof Map<?, ?> ? map(p) : null);
            }
            if (raw.get("observation") instanceof Map<?, ?> obs) {
                task.setLastObservation(map(obs));
            }
            if (raw.get("error") instanceof Map<?, ?> err) {
                Map<String, Object> e = map(err);
                task.setError(string(e.get("code")), string(e.get("message")), bool(metadata.get("retryable")));
            }
            if ("RECOVERY_STARTED".equals(type)) {
                task.recordRecoveryAttempt(integer(metadata.get("attempt")));
            }

            // ---- application status and required action
            ApplicationStatus appStatus = enumOrNull(ApplicationStatus.class, raw.get("applicationStatus"));
            if (appStatus != null) {
                if (TransitionPolicy.canTransition(app.getStatus(), appStatus)) {
                    state.transition(app, appStatus, "agent: " + type);
                } else {
                    rejected++;
                    log.warn("Ignored illegal application transition {} -> {} for application {} (event {})",
                            app.getStatus(), appStatus, app.getId(), type);
                }
            }
            applyRequiredAction(app, task, type, taskStatus, metadata, string(raw.get("message")), now);
            app.touchActivity(now);

            // ---- side effects
            UUID approvalId = uuidOrNull(metadata.get("approvalId"));
            if (approvalId != null && ("BROWSER_ACTION_COMPLETED".equals(type) || "SUBMISSION_COMPLETED".equals(type))) {
                approvalService.consume(taskId, approvalId);
                if ("BROWSER_ACTION_COMPLETED".equals(type) && metadata.get("documents") != null) {
                    recordPortalUploads(task, approvalId);
                }
            }
            String checkpointId = string(metadata.get("checkpointId"));
            if (checkpointId != null && !checkpoints.existsByTaskIdAndCheckpointId(taskId, checkpointId)) {
                checkpoints.save(new AgentCheckpoint(taskId, taskId.toString(), truncate(checkpointId, 64),
                        truncate(string(raw.get("step")), 64), now));
            }
            notifyFor(type, task, raw, metadata);
            if (TERMINAL.contains(type) || "SUBMISSION_COMPLETED".equals(type)) {
                audit.record(AuditEntry.of(AuditCategory.AGENT, type).user(task.getUserId())
                        .actor(ActorType.AGENT, taskId.toString()).resource("AGENT_TASK", taskId)
                        .outcome("TASK_FAILED".equals(type) ? AuditOutcome.FAILURE : AuditOutcome.SUCCESS)
                        .summary(truncate(defaultIfBlank(string(raw.get("message")), type), 450)));
            }
            hub.publishAfterCommit(ExecutionEventDto.from(saved));
        }
        return new IngestResult(accepted, duplicates, rejected);
    }

    private void applyRequiredAction(Application app, AgentTask task, String type, TaskStatus taskStatus,
                                     Map<String, Object> metadata, String message, Instant now) {
        if (taskStatus == TaskStatus.WAITING_APPROVAL) {
            Map<String, Object> ra = new LinkedHashMap<>();
            ra.put("type", "APPROVAL");
            ra.put("message", defaultIfBlank(message, "Your approval is needed."));
            ra.put("approvalId", string(metadata.get("approvalId")));
            ra.put("taskId", task.getId().toString());
            app.setRequiredAction(ra, now);
        } else if (taskStatus == TaskStatus.WAITING_CITIZEN) {
            Map<String, Object> ra = new LinkedHashMap<>();
            ra.put("type", "CITIZEN_INPUT");
            ra.put("message", defaultIfBlank(message, "Your input is needed."));
            ra.put("taskId", task.getId().toString());
            app.setRequiredAction(ra, now);
        } else if (taskStatus == TaskStatus.RUNNING) {
            app.setRequiredAction(null, now);
        } else if (TERMINAL.contains(type)) {
            ApplicationStatus s = app.getStatus();
            if ((s == ApplicationStatus.ACTION_REQUIRED || s == ApplicationStatus.SUBMISSION_UNCERTAIN)
                    && metadata.get("requiredAction") instanceof Map<?, ?> ra) {
                Map<String, Object> copy = map(ra);
                copy.put("taskId", task.getId().toString());
                app.setRequiredAction(copy, now);
            } else if (s != ApplicationStatus.ACTION_REQUIRED) {
                app.setRequiredAction(null, now);
            }
        }
    }

    private void recordPortalUploads(AgentTask task, UUID approvalId) {
        approvals.findByIdAndTaskId(approvalId, task.getId()).ifPresent(a -> {
            Object docs = a.getDetails() == null ? null : a.getDetails().get("documents");
            if (docs instanceof List<?> list) {
                for (Object d : list) {
                    if (d instanceof Map<?, ?> m) {
                        UUID docId = uuidOrNull(m.get("documentId"));
                        if (docId != null) {
                            accessLog.record(docId, task.getUserId(), AccessActorType.AGENT, task.getId().toString(), task.getId(),
                                    AccessAction.PORTAL_UPLOAD, "Uploaded to the portal under approval " + approvalId);
                        }
                    }
                }
            }
        });
    }

    private void notifyFor(String type, AgentTask task, Map<String, Object> raw, Map<String, Object> metadata) {
        String link = "/agent/" + task.getId();
        String message = truncate(defaultIfBlank(string(raw.get("message")), ""), 900);
        switch (type) {
            case "CITIZEN_INPUT_REQUIRED" -> notifications.notify(task.getUserId(), NotificationType.CITIZEN_INPUT_REQUIRED,
                    "Your input is needed", message, link);
            case "RECEIPT_VERIFIED" -> notifications.notify(task.getUserId(), NotificationType.RECEIPT_VERIFIED,
                    "Application submitted", message, "/applications/" + task.getApplicationId());
            case "SUBMISSION_UNCERTAIN" -> notifications.notify(task.getUserId(), NotificationType.SUBMISSION_UNCERTAIN,
                    "Submission not confirmed", message, "/applications/" + task.getApplicationId());
            case "TASK_FAILED" -> notifications.notify(task.getUserId(), NotificationType.TASK_FAILED,
                    "The agent run stopped", message, link);
            default -> {
                // APPROVAL_REQUIRED notifications are created when the approval itself is created.
            }
        }
    }

    // ---------------------------------------------------------------- tolerant readers for the untyped batch

    private static UUID uuid(Object v, String field) {
        UUID u = uuidOrNull(v);
        if (u == null) {
            throw ApiException.validation(field, "must be a UUID");
        }
        return u;
    }

    private static UUID uuidOrNull(Object v) {
        if (v == null) {
            return null;
        }
        try {
            return UUID.fromString(v.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String string(Object v) {
        return v == null ? null : v.toString();
    }

    private static Boolean bool(Object v) {
        return v instanceof Boolean b ? b : null;
    }

    private static Integer integer(Object v) {
        return v instanceof Number n ? n.intValue() : null;
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, Object v) {
        if (v == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, v.toString());
        } catch (IllegalArgumentException e) {
            throw new ApiException("INVALID_STATUS", HttpStatus.UNPROCESSABLE_ENTITY, "Unknown status " + v);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object v) {
        if (!(v instanceof Map<?, ?> m)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        ((Map<Object, Object>) m).forEach((k, val) -> out.put(String.valueOf(k), val));
        return out;
    }

    private static Instant instant(Object v, Instant fallback) {
        if (v == null) {
            return fallback;
        }
        try {
            return Instant.parse(v.toString());
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static String defaultIfBlank(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }
}
