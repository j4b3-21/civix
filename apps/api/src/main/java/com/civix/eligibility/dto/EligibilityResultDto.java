package com.civix.eligibility.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Deterministic eligibility result produced by the agent (docs/AGENT_CONTRACTS.md §3.1). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EligibilityResultDto(
        String serviceCode,
        Integer serviceVersion,
        String outcome,
        List<RuleEvaluationDto> rules,
        List<String> matched,
        List<String> failed,
        List<String> unknown,
        List<String> missingFields,
        List<String> notEvaluated,
        String explanation,
        String evaluatedAt,
        String engineVersion) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RuleEvaluationDto(
            String ruleId,
            Integer ruleVersion,
            String field,
            String operator,
            Object expected,
            Object actual,
            String result,
            String fieldStatus,
            String explanation,
            EvidenceDto evidence) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvidenceDto(String sourceUrl, String sourceTitle, String retrievedAt, String excerpt) {
    }
}
