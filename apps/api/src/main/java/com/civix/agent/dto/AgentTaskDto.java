package com.civix.agent.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.civix.approvals.dto.ApprovalDto;

/** docs/API.md AgentTaskDto. {@code userId} is only populated for the admin view. */
public record AgentTaskDto(UUID id, UUID applicationId, String serviceName, String status, String currentStep,
                           String currentAgent, Instant createdAt, Instant startedAt, Instant updatedAt,
                           Instant completedAt, int recoveryAttempts, ApprovalDto pendingApproval,
                           Map<String, Object> pendingCitizenInput, Map<String, Object> lastObservation,
                           TaskError error, long lastSequence,
                           @JsonInclude(JsonInclude.Include.NON_NULL) UUID userId) {

    public record TaskError(String code, String message) {
    }
}
