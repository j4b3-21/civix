package com.civix.citizen.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.civix.citizen.domain.FieldSource;
import com.civix.citizen.domain.ProfileFieldStatus;
import com.civix.citizen.domain.ProfileFieldType;
import com.civix.citizen.domain.Sensitivity;
import com.fasterxml.jackson.annotation.JsonInclude;

public record ProfileFieldDto(
        String key,
        String label,
        ProfileFieldType type,
        String group,
        String purpose,
        Sensitivity sensitivity,
        boolean core,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> options,
        @JsonInclude(JsonInclude.Include.NON_NULL) String unit,
        Object value,
        ProfileFieldStatus status,
        FieldSource source,
        UUID sourceDocumentId,
        BigDecimal confidence,
        Instant updatedAt) {
}
