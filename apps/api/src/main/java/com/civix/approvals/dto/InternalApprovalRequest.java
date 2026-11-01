package com.civix.approvals.dto;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.civix.approvals.domain.ActionClass;
import com.civix.approvals.domain.ApprovalKind;

/** Agent → API (docs/AGENT_CONTRACTS.md §2.4). */
public record InternalApprovalRequest(
        @NotNull UUID taskId,
        @NotNull UUID applicationId,
        @NotNull ApprovalKind kind,
        @NotNull ActionClass actionClass,
        @NotBlank @Size(max = 500) String summary,
        Map<String, Object> details,
        @NotNull @Pattern(regexp = "^[0-9a-f]{64}$") String payloadDigest,
        @NotNull @Min(1) Integer dataVersion,
        @NotNull @Min(60) @Max(86400) Integer expiresInSeconds) {
}
