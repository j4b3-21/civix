package com.civix.approvals.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.civix.approvals.domain.ApprovalDecision;

public record DecisionRequest(@NotNull ApprovalDecision decision, @NotNull @Min(1) Integer dataVersion,
                              @Size(max = 500) String reason) {
}
