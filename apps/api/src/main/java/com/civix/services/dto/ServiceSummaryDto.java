package com.civix.services.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.civix.services.domain.AutomationStatus;
import com.civix.services.domain.ServiceCategory;
import com.civix.services.domain.ServiceVerificationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ServiceSummaryDto(
        UUID id,
        String code,
        int version,
        String name,
        String summary,
        String department,
        String ministry,
        ServiceCategory category,
        ServiceJurisdictionDto jurisdiction,
        ServiceVerificationStatus verificationStatus,
        AutomationStatus automationStatus,
        String officialUrl,
        @JsonProperty("isTestService") boolean isTestService,
        DeadlineDto deadline,
        LocalDate lastVerifiedAt,
        EligibilitySummaryDto eligibility) {
}
