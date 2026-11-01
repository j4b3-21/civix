package com.civix.citizen.domain;

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

/** Provenance transitions only — values are intentionally never stored here (data minimisation). */
@Entity
@Table(name = "profile_field_history")
public class ProfileFieldHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "field_key", nullable = false, length = 64)
    private String fieldKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 16)
    private ProfileFieldStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 16)
    private ProfileFieldStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 24)
    private FieldSource source;

    @Column(name = "source_document_id")
    private UUID sourceDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "changed_by", nullable = false, length = 16)
    private ChangedBy changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ProfileFieldHistory() {
    }

    public ProfileFieldHistory(UUID userId, String fieldKey, ProfileFieldStatus oldStatus, ProfileFieldStatus newStatus,
                               FieldSource source, UUID sourceDocumentId, ChangedBy changedBy, Instant changedAt) {
        this.userId = userId;
        this.fieldKey = fieldKey;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.source = source;
        this.sourceDocumentId = sourceDocumentId;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public ProfileFieldStatus getOldStatus() {
        return oldStatus;
    }

    public ProfileFieldStatus getNewStatus() {
        return newStatus;
    }

    public ChangedBy getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
