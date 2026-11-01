package com.civix.citizen.dto;

import java.util.List;

public record CompletenessDto(int percent, int coreFieldCount, int coreFilledCount, List<String> missingCoreFields) {
}
