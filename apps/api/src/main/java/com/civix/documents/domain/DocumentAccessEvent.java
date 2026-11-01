package com.civix.documents.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Who touched a document, when and why. Kept after the document is deleted. */
@Entity
@Table(name = "document_access_events")
public class DocumentAccessEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 8)
    private AccessActorType actorType;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Column(name = "task_id")
    private UUID taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16)
    private AccessAction action;

    @Column(name = "purpose", length = 64)
    private String purpose;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected DocumentAccessEvent() {
    }

    public DocumentAccessEvent(UUID documentId, UUID userId, AccessActorType actorType, String actorId, UUID taskId,
                               AccessAction action, String purpose, Instant occurredAt) {
        this.documentId = documentId;
        this.userId = userId;
        this.actorType = actorType;
        this.actorId = actorId;
        this.taskId = taskId;
        this.action = action;
        this.purpose = purpose;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getUserId() {
        return userId;
    }

    public AccessActorType getActorType() {
        return actorType;
    }

    public String getActorId() {
        return actorId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public AccessAction getAction() {
        return action;
    }

    public String getPurpose() {
        return purpose;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
