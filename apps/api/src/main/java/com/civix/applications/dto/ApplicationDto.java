package com.civix.applications.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.civix.services.dto.ServiceSummaryDto;

public record ApplicationDto(UUID id, ServiceSummaryDto service, String status, int dataVersion, String referenceNumber,
                             String eligibilityOutcome, Instant createdAt, Instant updatedAt, Instant lastActivityAt,
                             Instant submittedAt, Map<String, Object> requiredAction, UUID latestTaskId,
                             @JsonProperty("isTestEnvironment") boolean isTestEnvironment) {
}
