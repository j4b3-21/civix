package com.civix.citizen.service;

import java.util.UUID;

/**
 * Published synchronously inside the transaction whenever profile values or documents change. Listeners bump
 * {@code data_version} of open applications and invalidate their outstanding approvals.
 */
public record CitizenDataChangedEvent(UUID userId, String reason) {
}
