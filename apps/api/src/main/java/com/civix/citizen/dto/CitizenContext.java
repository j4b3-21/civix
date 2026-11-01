package com.civix.citizen.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code CitizenContext} sent to the agent (docs/AGENT_CONTRACTS.md §3.1). Contains only fields the citizen
 * consented to for the purpose; missing fields are absent.
 */
public record CitizenContext(UUID userId, Map<String, FieldValue> fields, List<String> consents) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldValue(Object value, String status, String source, UUID sourceDocumentId, Double confidence) {
    }
}
