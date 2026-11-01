package com.civix.documents.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.civix.agent.client.AgentClient;
import com.civix.agent.client.AgentWire.ExtractRequest;
import com.civix.agent.client.AgentWire.ExtractResponse;
import com.civix.agent.client.AgentWire.OtherDocument;
import com.civix.citizen.dto.CitizenContext;
import com.civix.citizen.service.CitizenContextService;
import com.civix.common.api.ApiException;
import com.civix.consent.domain.ConsentPurpose;
import com.civix.documents.domain.AccessAction;
import com.civix.documents.domain.AccessActorType;
import com.civix.documents.domain.Document;
import com.civix.documents.domain.DocumentExtraction;
import com.civix.documents.domain.ExtractionStatus;
import com.civix.documents.repository.DocumentExtractionRepository;
import com.civix.documents.repository.DocumentRepository;

/**
 * Runs document extraction in the agent after the upload commits. No database connection is held while the agent
 * works: the attempt is opened in one transaction and closed in another.
 */
@Service
public class DocumentExtractionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentExtractionService.class);
    private static final java.time.Duration STALE_PROCESSING = java.time.Duration.ofMinutes(10);

    private final DocumentRepository documents;
    private final DocumentExtractionRepository extractions;
    private final DocumentAccessLog accessLog;
    private final CitizenContextService contexts;
    private final AgentClient agent;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DocumentExtractionService(DocumentRepository documents, DocumentExtractionRepository extractions,
                                     DocumentAccessLog accessLog, CitizenContextService contexts, AgentClient agent,
                                     PlatformTransactionManager txManager, Clock clock) {
        this.documents = documents;
        this.extractions = extractions;
        this.accessLog = accessLog;
        this.contexts = contexts;
        this.agent = agent;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExtractionRequested(DocumentExtractionRequested event) {
        run(event.documentId(), event.userId());
    }

    private record Started(UUID extractionId, ExtractRequest request) {
    }

    public void run(UUID documentId, UUID userId) {
        Started started;
        try {
            started = tx.execute(status -> start(documentId, userId));
        } catch (RuntimeException e) {
            log.warn("Could not start extraction for document {}: {}", documentId, e.getClass().getSimpleName());
            return;
        }
        if (started == null) {
            return;
        }
        try {
            ExtractResponse response = agent.extractDocument(started.request());
            if (response == null || (response.documentId() != null && !response.documentId().equals(documentId))) {
                throw new IllegalStateException("extraction response does not match the document");
            }
            tx.executeWithoutResult(status -> complete(documentId, started.extractionId(), response));
        } catch (ApiException e) {
            tx.executeWithoutResult(status -> fail(documentId, started.extractionId(),
                    "The document reader is not available right now. You can retry the extraction later."));
        } catch (RuntimeException e) {
            log.warn("Extraction of document {} failed: {}", documentId, e.getClass().getSimpleName());
            tx.executeWithoutResult(status -> fail(documentId, started.extractionId(),
                    "The document could not be read automatically. You can enter the values yourself."));
        }
    }

    private Started start(UUID documentId, UUID userId) {
        Document doc = documents.findActiveForUpdate(documentId, userId).orElse(null);
        if (doc == null) {
            return null;
        }
        DocumentExtraction previous = extractions.findFirstByDocumentIdOrderByAttemptDesc(documentId).orElse(null);
        if (doc.getExtractionStatus() == ExtractionStatus.PROCESSING && previous != null
                && previous.getStartedAt().isAfter(clock.instant().minus(STALE_PROCESSING))) {
            return null; // another run is active
        }
        int attempt = extractions.maxAttempt(documentId) + 1;
        DocumentExtraction extraction = extractions.save(new DocumentExtraction(documentId, attempt, clock.instant()));
        doc.markExtraction(ExtractionStatus.PROCESSING, clock.instant());
        documents.save(doc);
        accessLog.record(documentId, userId, AccessActorType.SYSTEM, "document-extraction", null, AccessAction.EXTRACT,
                "DOCUMENT_EXTRACTION");

        CitizenContext context = contexts.build(userId, ConsentPurpose.DOCUMENT_PROCESSING);
        List<OtherDocument> others = new ArrayList<>();
        List<Document> otherDocs = documents.findByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(userId).stream()
                .filter(d -> !d.getId().equals(documentId))
                .limit(20)
                .toList();
        if (!otherDocs.isEmpty()) {
            for (DocumentExtraction e : extractions.findLatestFor(otherDocs.stream().map(Document::getId).toList())) {
                if (e.getStatus() != ExtractionStatus.COMPLETED) {
                    continue;
                }
                Document other = otherDocs.stream().filter(d -> d.getId().equals(e.getDocumentId())).findFirst().orElse(null);
                if (other != null) {
                    others.add(new OtherDocument(other.getId(), other.getCategory().name(),
                            e.getFields() == null ? List.of() : e.getFields()));
                }
            }
        }
        ExtractRequest request = new ExtractRequest(documentId, doc.getStorageKey(), doc.getMimeType(),
                doc.getCategory().name(), context, others.isEmpty() ? null : others);
        return new Started(extraction.getId(), request);
    }

    private void complete(UUID documentId, UUID extractionId, ExtractResponse r) {
        DocumentExtraction extraction = extractions.findById(extractionId).orElse(null);
        Document doc = documents.findById(documentId).orElse(null);
        if (extraction == null || doc == null || doc.isDeleted()) {
            return;
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        for (Map<String, Object> f : r.fields() == null ? List.<Map<String, Object>>of() : r.fields()) {
            Map<String, Object> clean = new LinkedHashMap<>();
            clean.put("key", f.get("key"));
            clean.put("label", f.get("label"));
            clean.put("value", f.get("value"));
            clean.put("confidence", f.get("confidence"));
            clean.put("profileField", f.get("profileField"));
            fields.add(clean);
        }
        List<Map<String, Object>> discrepancies = new ArrayList<>();
        for (Map<String, Object> d : r.discrepancies() == null ? List.<Map<String, Object>>of() : r.discrepancies()) {
            Map<String, Object> clean = new LinkedHashMap<>();
            clean.put("field", d.get("field"));
            clean.put("message", d.get("message"));
            clean.put("severity", d.get("severity"));
            clean.put("documentIds", d.get("documentIds"));
            discrepancies.add(clean);
        }
        String category = r.classification() == null ? null : truncate(r.classification().category(), 32);
        BigDecimal confidence = r.classification() == null ? null : confidence(r.classification().confidence());
        Boolean matches = r.classification() == null ? null : r.classification().declaredCategoryMatches();
        extraction.complete(category, confidence, matches, fields, discrepancies,
                r.warnings() == null ? List.of() : r.warnings(), truncate(r.engine(), 64), r.textCharacters(),
                clock.instant());
        extractions.save(extraction);
        doc.markExtraction(ExtractionStatus.COMPLETED, clock.instant());
        documents.save(doc);
    }

    private void fail(UUID documentId, UUID extractionId, String message) {
        extractions.findById(extractionId).ifPresent(e -> {
            e.fail(message, clock.instant());
            extractions.save(e);
        });
        documents.findById(documentId).filter(d -> !d.isDeleted()).ifPresent(d -> {
            d.markExtraction(ExtractionStatus.FAILED, clock.instant());
            documents.save(d);
        });
    }

    private static BigDecimal confidence(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return BigDecimal.valueOf(clamped).setScale(3, RoundingMode.HALF_UP);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
