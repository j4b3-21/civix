package com.civix.documents.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code accept}: profile field keys (with the possibly corrected value) to write to the profile; {@code reject}: keys to discard. */
public record ReviewRequest(
        @NotNull @Size(max = 100) List<@Valid @NotNull AcceptedField> accept,
        @Size(max = 100) List<@NotBlank @Size(max = 64) String> reject) {

    public record AcceptedField(@NotBlank @Size(max = 64) @Pattern(regexp = "^[a-z0-9_]+$") String key, Object value) {
    }
}
