package com.civix.services.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.civix.services.domain.AutomationStatus;
import com.civix.services.domain.ServiceCategory;
import com.civix.services.domain.ServiceVerificationStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {@link ServiceSummaryDto} members plus the full description, rules, documents, procedure and sources. */
public record ServiceDetailDto(
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
        EligibilitySummaryDto eligibility,
        String description,
        List<String> benefits,
        List<EligibilityRuleDto> eligibilityRules,
        List<RequiredDocumentDto> requiredDocuments,
        List<ProcedureStepDto> procedure,
        List<SourceDto> sources,
        List<String> portalDomains,
        String disclaimer) {

    public record EligibilityRuleDto(String ruleId, int ruleVersion, String description, String field, String operator,
                                     Object value, String sourceUrl) {
    }

    public record RequiredDocumentDto(String category, String description, boolean mandatory) {
    }

    public record ProcedureStepDto(int order, String title, String description) {
    }

    public record SourceDto(String url, String title, String kind, LocalDate retrievedAt) {
    }
}
