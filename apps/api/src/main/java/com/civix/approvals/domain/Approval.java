package com.civix.approvals.domain;

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

/** A human-in-the-loop approval requested by the agent before a gated action (docs/WORKFLOWS.md §4). */
@Entity
@Table(name = "approvals")
public class Approval {

    @Id
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 24)
    private ApprovalKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_class", nullable = false, length = 16)
    private ActionClass actionClass;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false)
    private Map<String, Object> details;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "payload_digest", nullable = false, length = 64)
    private String payloadDigest;

    @Column(name = "data_version", nullable = false)
    private int dataVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ApprovalStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason", length = 500)
    private String decisionReason;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "invalidated_reason", length = 64)
    private String invalidatedReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Approval() {
    }

    public Approval(UUID taskId, UUID applicationId, UUID userId, ApprovalKind kind, ActionClass actionClass,
                    String summary, Map<String, Object> details, String payloadDigest, int dataVersion,
                    Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.taskId = taskId;
        this.applicationId = applicationId;
        this.userId = userId;
        this.kind = kind;
        this.actionClass = actionClass;
        this.summary = summary;
        this.details = details;
        this.payloadDigest = payloadDigest;
        this.dataVersion = dataVersion;
        this.status = ApprovalStatus.PENDING;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void approve(String reason, Instant now) {
        this.status = ApprovalStatus.APPROVED;
        this.decidedAt = now;
        this.decisionReason = reason;
    }

    public void reject(String reason, Instant now) {
        this.status = ApprovalStatus.REJECTED;
        this.decidedAt = now;
        this.decisionReason = reason;
    }

    public void expire() {
        this.status = ApprovalStatus.EXPIRED;
    }

    public void invalidate(String reason) {
        this.status = ApprovalStatus.INVALIDATED;
        this.invalidatedReason = reason;
    }

    public void consume(Instant now) {
        this.status = ApprovalStatus.CONSUMED;
        this.consumedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public ApprovalKind getKind() {
        return kind;
    }

    public ActionClass getActionClass() {
        return actionClass;
    }

    public String getSummary() {
        return summary;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public String getPayloadDigest() {
        return payloadDigest;
    }

    public int getDataVersion() {
        return dataVersion;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public String getInvalidatedReason() {
        return invalidatedReason;
    }
}
