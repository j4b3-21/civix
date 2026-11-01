package com.civix.audit.dto;

import java.time.Instant;
import java.util.UUID;

import com.civix.audit.domain.AuditCategory;
import com.civix.audit.domain.AuditEvent;
import com.civix.audit.domain.AuditOutcome;

public record AuditEventDto(UUID id, AuditCategory category, String action, String resourceType, String resourceId,
                            AuditOutcome outcome, Instant occurredAt, String summary) {

    public static AuditEventDto from(AuditEvent e) {
        return new AuditEventDto(e.getId(), e.getCategory(), e.getAction(), e.getResourceType(), e.getResourceId(),
                e.getOutcome(), e.getOccurredAt(), e.getSummary());
    }
}
