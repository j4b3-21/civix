package com.civix.documents.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.civix.documents.domain.DocumentCategory;
import com.civix.documents.domain.ExtractionStatus;
import com.civix.documents.domain.ReviewStatus;

public record DocumentDto(UUID id, String fileName, DocumentCategory category, String mimeType, long sizeBytes,
                          String sha256, Instant uploadedAt, ExtractionStatus extractionStatus,
                          ReviewStatus reviewStatus, ExtractionDto extraction) {

    public record ExtractionDto(ClassificationDto classification, List<ExtractedFieldDto> fields,
                                List<DiscrepancyDto> discrepancies, List<String> warnings, String engine,
                                Instant completedAt, String errorMessage) {
    }

    public record ClassificationDto(String category, BigDecimal confidence, Boolean declaredCategoryMatches) {
    }

    public record ExtractedFieldDto(String key, String label, Object value, BigDecimal confidence, String profileField) {
    }

    public record DiscrepancyDto(String field, String message, String severity) {
    }
}
