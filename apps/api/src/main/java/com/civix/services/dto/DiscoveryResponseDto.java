package com.civix.services.dto;

import java.util.List;

import com.civix.eligibility.dto.EligibilityResultDto;

public record DiscoveryResponseDto(Interpretation interpretation, List<Result> results, String generatedAt,
                                   String notice) {

    public record Interpretation(String summary, List<String> categories, List<String> keywords, String state,
                                 boolean usedLlm) {
    }

    public record Result(ServiceSummaryDto service, double score, List<String> matchReasons,
                         EligibilityResultDto eligibility) {
    }
}
