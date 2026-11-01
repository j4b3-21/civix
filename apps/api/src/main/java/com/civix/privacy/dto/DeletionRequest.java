package com.civix.privacy.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record DeletionRequest(@NotNull DeletionScope scope,
                              @NotNull @Pattern(regexp = "^DELETE$", message = "type DELETE to confirm") String confirm) {

    public enum DeletionScope { DOCUMENTS, PROFILE, ACCOUNT }
}
