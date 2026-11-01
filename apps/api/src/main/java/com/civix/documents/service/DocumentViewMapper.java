package com.civix.documents.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.civix.documents.domain.Document;
import com.civix.documents.domain.DocumentExtraction;
import com.civix.documents.domain.ExtractionStatus;
import com.civix.documents.dto.DocumentDto;

@Component
public class DocumentViewMapper {

    public DocumentDto toDto(Document d, DocumentExtraction latest) {
        return new DocumentDto(d.getId(), d.getFileName(), d.getCategory(), d.getMimeType(), d.getSizeBytes(),
                d.getSha256(), d.getUploadedAt(), d.getExtractionStatus(), d.getReviewStatus(), extraction(latest));
    }

    private static DocumentDto.ExtractionDto extraction(DocumentExtraction e) {
        if (e == null || (e.getStatus() != ExtractionStatus.COMPLETED && e.getStatus() != ExtractionStatus.FAILED)) {
            return null;
        }
        DocumentDto.ClassificationDto classification = e.getClassificationCategory() == null ? null
                : new DocumentDto.ClassificationDto(e.getClassificationCategory(), e.getClassificationConfidence(),
                        e.getDeclaredCategoryMatches());
        List<DocumentDto.ExtractedFieldDto> fields = new ArrayList<>();
        for (Map<String, Object> f : nullSafe(e.getFields())) {
            fields.add(new DocumentDto.ExtractedFieldDto(str(f.get("key")), str(f.get("label")), f.get("value"),
                    decimal(f.get("confidence")), str(f.get("profileField"))));
        }
        List<DocumentDto.DiscrepancyDto> discrepancies = new ArrayList<>();
        for (Map<String, Object> d : nullSafe(e.getDiscrepancies())) {
            discrepancies.add(new DocumentDto.DiscrepancyDto(str(d.get("field")), str(d.get("message")),
                    str(d.get("severity"))));
        }
        return new DocumentDto.ExtractionDto(classification, fields, discrepancies,
                e.getWarnings() == null ? List.of() : e.getWarnings(), e.getEngine(), e.getCompletedAt(),
                e.getErrorMessage());
    }

    private static List<Map<String, Object>> nullSafe(List<Map<String, Object>> l) {
        return l == null ? List.of() : l;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    static BigDecimal decimal(Object o) {
        if (o instanceof Number n) {
            try {
                return new BigDecimal(n.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
