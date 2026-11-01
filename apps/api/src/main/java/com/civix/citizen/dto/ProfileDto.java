package com.civix.citizen.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProfileDto(UUID userId, Instant updatedAt, CompletenessDto completeness, List<ProfileFieldDto> fields) {
}
