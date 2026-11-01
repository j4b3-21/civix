package com.civix.consent.dto;

import java.time.Instant;

import com.civix.consent.domain.ConsentPurpose;

public record ConsentDto(ConsentPurpose purpose, boolean granted, String noticeVersion, Instant grantedAt,
                         Instant revokedAt, String description) {
}
