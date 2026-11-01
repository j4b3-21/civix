package com.civix.documents.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One extraction attempt. Extracted values reach the profile only after the citizen reviews them. */
@Entity
@Table(name = "document_extractions")
public class DocumentExtraction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "attempt", nullable = false)
    private int attempt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ExtractionStatus status;

    @Column(name = "classification_category", length = 32)
    private String classificationCategory;

    @Column(name = "classification_confidence", precision = 4, scale = 3)
    private BigDecimal classificationConfidence;

    @Column(name = "declared_category_matches")
    private Boolean declaredCategoryMatches;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fields", nullable = false)
    private List<Map<String, Object>> fields;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "discrepancies", nullable = false)
    private List<Map<String, Object>> discrepancies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", nullable = false)
    private List<String> warnings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "review")
    private Map<String, Object> review;

    @Column(name = "engine", length = 64)
    private String engine;

    @Column(name = "text_characters")
    private Integer textCharacters;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected DocumentExtraction() {
    }

    public DocumentExtraction(UUID documentId, int attempt, Instant now) {
        this.documentId = documentId;
        this.attempt = attempt;
        this.status = ExtractionStatus.PROCESSING;
        this.fields = List.of();
        this.discrepancies = List.of();
        this.warnings = List.of();
        this.startedAt = now;
    }

    public void complete(String classificationCategory, BigDecimal classificationConfidence, Boolean declaredMatches,
                         List<Map<String, Object>> fields, List<Map<String, Object>> discrepancies, List<String> warnings,
                         String engine, Integer textCharacters, Instant now) {
        this.status = ExtractionStatus.COMPLETED;
        this.classificationCategory = classificationCategory;
        this.classificationConfidence = classificationConfidence;
        this.declaredCategoryMatches = declaredMatches;
        this.fields = fields;
        this.discrepancies = discrepancies;
        this.warnings = warnings;
        this.engine = engine;
        this.textCharacters = textCharacters;
        this.errorMessage = null;
        this.completedAt = now;
    }

    public void fail(String errorMessage, Instant now) {
        this.status = ExtractionStatus.FAILED;
        this.errorMessage = errorMessage == null ? null : errorMessage.substring(0, Math.min(500, errorMessage.length()));
        this.completedAt = now;
    }

    public void recordReview(Map<String, Object> review) {
        this.review = review;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getAttempt() {
        return attempt;
    }

    public ExtractionStatus getStatus() {
        return status;
    }

    public String getClassificationCategory() {
        return classificationCategory;
    }

    public BigDecimal getClassificationConfidence() {
        return classificationConfidence;
    }

    public Boolean getDeclaredCategoryMatches() {
        return declaredCategoryMatches;
    }

    public List<Map<String, Object>> getFields() {
        return fields;
    }

    public List<Map<String, Object>> getDiscrepancies() {
        return discrepancies;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public Map<String, Object> getReview() {
        return review;
    }

    public String getEngine() {
        return engine;
    }

    public Integer getTextCharacters() {
        return textCharacters;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
