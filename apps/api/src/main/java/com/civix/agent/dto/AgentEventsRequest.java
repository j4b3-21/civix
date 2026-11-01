package com.civix.agent.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Agent → API event batch (docs/AGENT_CONTRACTS.md §2.3). Events are kept as maps on purpose: the contract
 * distinguishes "key absent" from "key present with null" (e.g. {@code pendingCitizenInput: null} clears it).
 */
public record AgentEventsRequest(@NotNull @Size(min = 1, max = 500) List<Map<String, Object>> events) {
}
