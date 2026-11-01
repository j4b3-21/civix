package com.civix.consent.service;

import java.util.UUID;

import com.civix.consent.domain.ConsentPurpose;

/** Published synchronously (inside the transaction) whenever a consent is granted or revoked. */
public record ConsentChangedEvent(UUID userId, ConsentPurpose purpose, boolean granted) {
}
