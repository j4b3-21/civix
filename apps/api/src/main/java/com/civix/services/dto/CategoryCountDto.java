package com.civix.services.dto;

import com.civix.services.domain.ServiceCategory;

public record CategoryCountDto(ServiceCategory category, long count) {
}
