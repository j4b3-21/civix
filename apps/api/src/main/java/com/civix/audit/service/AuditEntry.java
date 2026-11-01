package com.civix.audit.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.civix.audit.domain.ActorType;
import com.civix.audit.domain.AuditCategory;
import com.civix.audit.domain.AuditOutcome;

/**
 * Fluent description of one audit event. Summaries and metadata must never contain field values, document
 * contents, citizen input or secrets — only identifiers, keys, labels and counts.
 */
public final class AuditEntry {

    final AuditCategory category;
    final String action;
    UUID userId;
    ActorType actorType = ActorType.USER;
    String actorId;
    String resourceType;
    String resourceId;
    AuditOutcome outcome = AuditOutcome.SUCCESS;
    String summary;
    final Map<String, Object> metadata = new LinkedHashMap<>();

    private AuditEntry(AuditCategory category, String action) {
        this.category = category;
        this.action = action;
    }

    public static AuditEntry of(AuditCategory category, String action) {
        return new AuditEntry(category, action);
    }

    public AuditEntry user(UUID userId) {
        this.userId = userId;
        return this;
    }

    public AuditEntry actor(ActorType type, String id) {
        this.actorType = type;
        this.actorId = id;
        return this;
    }

    public AuditEntry resource(String type, Object id) {
        this.resourceType = type;
        this.resourceId = id == null ? null : id.toString();
        return this;
    }

    public AuditEntry outcome(AuditOutcome outcome) {
        this.outcome = outcome;
        return this;
    }

    public AuditEntry summary(String summary) {
        this.summary = summary;
        return this;
    }

    public AuditEntry meta(String key, Object value) {
        if (value != null) {
            this.metadata.put(key, value);
        }
        return this;
    }
}
