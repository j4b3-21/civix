package com.civix.applications.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record CreateApplicationRequest(@NotNull UUID serviceId) {
}
