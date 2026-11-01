package com.civix.approvals.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.civix.approvals.domain.Approval;

public record ApprovalDto(UUID id, UUID taskId, UUID applicationId, String serviceName, String kind, String actionClass,
                          String summary, Map<String, Object> details, int dataVersion, String status, Instant createdAt,
                          Instant expiresAt, Instant decidedAt, String decisionReason) {

    public static ApprovalDto from(Approval a, String serviceName) {
        return new ApprovalDto(a.getId(), a.getTaskId(), a.getApplicationId(), serviceName, a.getKind().name(),
                a.getActionClass().name(), a.getSummary(), a.getDetails() == null ? Map.of() : a.getDetails(),
                a.getDataVersion(), a.getStatus().name(), a.getCreatedAt(), a.getExpiresAt(), a.getDecidedAt(),
                a.getDecisionReason());
    }
}
