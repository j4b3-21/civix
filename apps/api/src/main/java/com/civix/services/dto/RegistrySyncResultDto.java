package com.civix.services.dto;

import java.util.List;

public record RegistrySyncResultDto(int synced, int deactivated, List<String> errors) {
}
