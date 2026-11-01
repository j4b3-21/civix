package com.civix.agent.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.civix.agent.domain.ExecutionEvent;

/** Event envelope streamed over SSE (docs/AGENT_CONTRACTS.md §2.2). */
public record ExecutionEventDto(UUID eventId, UUID taskId, long sequence, String type, Instant timestamp, String agent,
                                String step, String message, Map<String, Object> metadata) {

    public static ExecutionEventDto from(ExecutionEvent e) {
        return new ExecutionEventDto(e.getEventId(), e.getTaskId(), e.getSequence(), e.getEventType(), e.getOccurredAt(),
                e.getAgent(), e.getStep(), e.getMessage(), e.getMetadata() == null ? Map.of() : e.getMetadata());
    }
}
