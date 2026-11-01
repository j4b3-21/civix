package com.civix.receipts.dto;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Agent → API: a receipt the Verification Agent observed (docs/AGENT_CONTRACTS.md §2.5). */
public record InternalReceiptRequest(
        @NotNull UUID taskId,
        @NotNull UUID applicationId,
        @NotBlank @Size(max = 128) String referenceNumber,
        @Size(max = 4000) String acknowledgementText,
        @Size(max = 1000) String portalUrl,
        @NotBlank @Size(max = 32) String verificationMethod,
        Map<String, Object> evidence,
        UUID screenshotId,
        @JsonProperty("isTestEnvironment") boolean isTestEnvironment) {
}
