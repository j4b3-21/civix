package com.civix.services.dto;

import java.util.List;

import com.civix.services.domain.JurisdictionLevel;

/** {@code states} are ISO 3166-2:IN codes; empty means all-India. */
public record ServiceJurisdictionDto(JurisdictionLevel level, List<String> states) {
}
