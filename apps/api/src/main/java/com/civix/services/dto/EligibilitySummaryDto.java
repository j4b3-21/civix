package com.civix.services.dto;

import java.time.Instant;

import com.civix.eligibility.domain.EligibilityOutcome;

public record EligibilitySummaryDto(EligibilityOutcome outcome, Instant evaluatedAt) {
}
