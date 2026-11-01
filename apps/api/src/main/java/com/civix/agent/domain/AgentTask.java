package com.civix.agent.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One autonomous run of the agent for an application. Status changes are validated by {@code TransitionPolicy}. */
@Entity
@Table(name = "agent_tasks")
public class AgentTask {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status;

    @Column(name = "intent", nullable = false, length = 16)
    private String intent;

    @Column(name = "current_step", length = 128)
    private String currentStep;

    @Column(name = "current_agent", length = 32)
    private String currentAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pending_citizen_input")
    private Map<String, Object> pendingCitizenInput;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_observation")
    private Map<String, Object> lastObservation;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "error_retryable")
    private Boolean errorRetryable;

    @Column(name = "recovery_attempts", nullable = false)
    private int recoveryAttempts;

    @Column(name = "last_event_sequence", nullable = false)
    private long lastEventSequence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancel_requested_at")
    private Instant cancelRequestedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected AgentTask() {
    }

    public AgentTask(UUID applicationId, UUID userId, Instant now) {
        this.id = UUID.randomUUID();
        this.applicationId = applicationId;
        this.userId = userId;
        this.status = TaskStatus.QUEUED;
        this.intent = "APPLY";
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Applies an already-validated status change and maintains the timestamps. */
    public void changeStatus(TaskStatus next, Instant now) {
        if (next == status) {
            return;
        }
        this.status = next;
        if (next == TaskStatus.RUNNING && startedAt == null) {
            startedAt = now;
        }
        if (next.isEnded()) {
            completedAt = now;
            pendingCitizenInput = null;
        } else {
            completedAt = null;
        }
        if (next == TaskStatus.QUEUED || next == TaskStatus.RUNNING) {
            clearError();
        }
        if (next != TaskStatus.WAITING_CITIZEN) {
            pendingCitizenInput = null;
        }
        this.updatedAt = now;
    }

    public void fail(String code, String message, boolean retryable, Instant now) {
        changeStatus(TaskStatus.FAILED, now);
        setError(code, message, retryable);
    }

    public void setError(String code, String message, Boolean retryable) {
        this.errorCode = code == null ? null : truncate(code, 64);
        this.errorMessage = message == null ? null : truncate(message, 1000);
        this.errorRetryable = retryable;
    }

    public void clearError() {
        this.errorCode = null;
        this.errorMessage = null;
        this.errorRetryable = null;
    }

    public void setProgress(String currentStep, String currentAgent) {
        if (currentStep != null) {
            this.currentStep = truncate(currentStep, 128);
        }
        if (currentAgent != null) {
            this.currentAgent = truncate(currentAgent, 32);
        }
    }

    public void setPendingCitizenInput(Map<String, Object> pending) {
        this.pendingCitizenInput = pending;
    }

    public void setLastObservation(Map<String, Object> observation) {
        this.lastObservation = observation;
    }

    public void recordRecoveryAttempt(Integer reportedAttempt) {
        int next = recoveryAttempts + 1;
        if (reportedAttempt != null && reportedAttempt > next) {
            next = reportedAttempt;
        }
        this.recoveryAttempts = next;
    }

    public void recordEvent(long sequence, Instant now) {
        if (sequence > lastEventSequence) {
            this.lastEventSequence = sequence;
        }
        this.updatedAt = now;
    }

    public void requestCancel(Instant now) {
        if (cancelRequestedAt == null) {
            cancelRequestedAt = now;
        }
        this.updatedAt = now;
    }

    public void resetForRetry(Instant now) {
        this.cancelRequestedAt = null;
        this.recoveryAttempts = 0;
        changeStatus(TaskStatus.QUEUED, now);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public String getIntent() {
        return intent;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public String getCurrentAgent() {
        return currentAgent;
    }

    public Map<String, Object> getPendingCitizenInput() {
        return pendingCitizenInput;
    }

    public Map<String, Object> getLastObservation() {
        return lastObservation;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Boolean getErrorRetryable() {
        return errorRetryable;
    }

    public int getRecoveryAttempts() {
        return recoveryAttempts;
    }

    public long getLastEventSequence() {
        return lastEventSequence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelRequestedAt() {
        return cancelRequestedAt;
    }
}
