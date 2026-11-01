package com.civix.citizen.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.ColumnTransformer;

/**
 * One profile value with provenance. {@code value} is a JSON scalar (string/number/boolean) stored in a jsonb
 * column; it is mapped as JSON text and converted by {@code JsonCodec} in the service layer.
 */
@Entity
@Table(name = "profile_fields")
public class ProfileField {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "field_key", nullable = false, length = 64)
    private String fieldKey;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "value", columnDefinition = "jsonb")
    private String valueJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ProfileFieldStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 24)
    private FieldSource source;

    @Column(name = "source_document_id")
    private UUID sourceDocumentId;

    @Column(name = "confidence", precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ProfileField() {
    }

    public ProfileField(UUID userId, String fieldKey, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.fieldKey = fieldKey;
        this.status = ProfileFieldStatus.MISSING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void set(String valueJson, ProfileFieldStatus status, FieldSource source, UUID sourceDocumentId,
                    BigDecimal confidence, Instant now) {
        this.valueJson = valueJson;
        this.status = status;
        this.source = source;
        this.sourceDocumentId = sourceDocumentId;
        this.confidence = confidence;
        this.updatedAt = now;
    }

    public void confirm(Instant now) {
        this.status = ProfileFieldStatus.USER_CONFIRMED;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public String getValueJson() {
        return valueJson;
    }

    public ProfileFieldStatus getStatus() {
        return status;
    }

    public FieldSource getSource() {
        return source;
    }

    public UUID getSourceDocumentId() {
        return sourceDocumentId;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
