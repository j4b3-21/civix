package com.civix.services.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.civix.services.domain.ServiceCategory;

public record DiscoverRequest(
        @NotBlank @Size(min = 3, max = 500) String query,
        @Pattern(regexp = "^IN-[A-Z]{2}$") String state,
        ServiceCategory category) {
}
