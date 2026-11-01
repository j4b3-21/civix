package com.civix.citizen.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotNull @Size(max = 100) List<@Valid @NotNull FieldValueInput> fields,
        @Size(max = 100) List<@NotBlank @Size(max = 64) String> confirmKeys) {

    /** {@code value: null} clears the field. Values are validated against the field definition. */
    public record FieldValueInput(
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-z0-9_]+$") String key,
            Object value) {
    }
}
