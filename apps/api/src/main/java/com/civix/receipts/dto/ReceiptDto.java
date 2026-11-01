package com.civix.receipts.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.civix.receipts.domain.ApplicationReceipt;

public record ReceiptDto(UUID id, UUID applicationId, UUID taskId, String referenceNumber, String acknowledgementText,
                         String portalUrl, String verificationMethod, Instant verifiedAt,
                         @JsonProperty("isTestEnvironment") boolean isTestEnvironment,
                         boolean artifactAvailable) {

    public static ReceiptDto from(ApplicationReceipt r) {
        return new ReceiptDto(r.getId(), r.getApplicationId(), r.getTaskId(), r.getReferenceNumber(),
                r.getAcknowledgementText(), r.getPortalUrl(), r.getVerificationMethod(), r.getVerifiedAt(),
                r.isTestEnvironment(), r.getArtifactStorageKey() != null);
    }
}
