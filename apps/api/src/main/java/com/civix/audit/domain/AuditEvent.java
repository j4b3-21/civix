package com.civix.audit.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only audit trail. Never contains field values, secrets or raw IP addresses. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 16)
    private AuditCategory category;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 8)
    private ActorType actorType;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Column(name = "resource_type", length = 32)
    private String resourceType;

    @Column(name = "resource_id", length = 64)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 8)
    private AuditOutcome outcome;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    private Map<String, Object> metadata;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEvent() {
    }

    public AuditEvent(UUID userId, AuditCategory category, String action, ActorType actorType, String actorId,
                      String resourceType, String resourceId, AuditOutcome outcome, String summary,
                      String correlationId, String ipHash, Map<String, Object> metadata, Instant occurredAt) {
        this.userId = userId;
        this.category = category;
        this.action = action;
        this.actorType = actorType;
        this.actorId = actorId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.outcome = outcome;
        this.summary = summary;
        this.correlationId = correlationId;
        this.ipHash = ipHash;
        this.metadata = metadata;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public AuditCategory getCategory() {
        return category;
    }

    public String getAction() {
        return action;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public String getActorId() {
        return actorId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public String getSummary() {
        return summary;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getIpHash() {
        return ipHash;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
