package com.civix.documents.dto;

import java.time.Instant;
import java.util.UUID;

import com.civix.documents.domain.AccessAction;
import com.civix.documents.domain.AccessActorType;
import com.civix.documents.domain.DocumentAccessEvent;

public record DocumentAccessDto(UUID id, AccessActorType actorType, String actorId, UUID taskId, AccessAction action,
                                String purpose, Instant occurredAt) {

    public static DocumentAccessDto from(DocumentAccessEvent e) {
        return new DocumentAccessDto(e.getId(), e.getActorType(), e.getActorId(), e.getTaskId(), e.getAction(),
                e.getPurpose(), e.getOccurredAt());
    }
}
