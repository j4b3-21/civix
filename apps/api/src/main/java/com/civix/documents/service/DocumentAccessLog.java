package com.civix.documents.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.civix.documents.domain.AccessAction;
import com.civix.documents.domain.AccessActorType;
import com.civix.documents.domain.DocumentAccessEvent;
import com.civix.documents.repository.DocumentAccessEventRepository;

@Component
public class DocumentAccessLog {

    private final DocumentAccessEventRepository events;
    private final Clock clock;

    public DocumentAccessLog(DocumentAccessEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void record(UUID documentId, UUID userId, AccessActorType actorType, String actorId, UUID taskId,
                       AccessAction action, String purpose) {
        events.save(new DocumentAccessEvent(documentId, userId, actorType, actorId, taskId, action, purpose, clock.instant()));
    }
}
