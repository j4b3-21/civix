package com.civix.approvals.dto;

import java.time.Instant;
import java.util.UUID;

public record InternalApprovalResponse(UUID approvalId, String status, Instant expiresAt) {
}
