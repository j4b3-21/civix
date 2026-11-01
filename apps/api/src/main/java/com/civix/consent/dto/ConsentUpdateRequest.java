package com.civix.consent.dto;

import jakarta.validation.constraints.NotNull;

import com.civix.consent.domain.ConsentPurpose;

public record ConsentUpdateRequest(@NotNull ConsentPurpose purpose, @NotNull Boolean granted) {
}
