package com.civix.documents.service;

import java.io.IOException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.civix.audit.domain.AuditCategory;
import com.civix.audit.service.AuditEntry;
import com.civix.audit.service.AuditService;
import com.civix.citizen.service.CitizenDataChangedEvent;
import com.civix.citizen.service.ProfileService;
import com.civix.common.api.ApiException;
import com.civix.common.crypto.Hashing;
import com.civix.common.storage.ObjectStorageService;
import com.civix.config.CivixProperties;
import com.civix.consent.domain.ConsentPurpose;
import com.civix.consent.service.ConsentService;
import com.civix.documents.domain.AccessAction;
import com.civix.documents.domain.AccessActorType;
import com.civix.documents.domain.Document;
import com.civix.documents.domain.DocumentCategory;
import com.civix.documents.domain.DocumentExtraction;
import com.civix.documents.domain.ExtractionStatus;
import com.civix.documents.dto.DocumentAccessDto;
import com.civix.documents.dto.DocumentDto;
import com.civix.documents.dto.ReviewRequest;
import com.civix.documents.repository.DocumentAccessEventRepository;
import com.civix.documents.repository.DocumentExtractionRepository;
import com.civix.documents.repository.DocumentRepository;

@Service
public class DocumentService {

    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private static final java.time.Duration STALE_PROCESSING = java.time.Duration.ofMinutes(10);

    private final DocumentRepository documents;
    private final DocumentExtractionRepository extractions;
    private final DocumentAccessEventRepository accessEvents;
    private final DocumentAccessLog accessLog;
    private final DocumentViewMapper mapper;
    private final ObjectStorageService storage;
    private final ConsentService consents;
    private final ProfileService profiles;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final String encryptionLabel;

    public DocumentService(DocumentRepository documents, DocumentExtractionRepository extractions,
                           DocumentAccessEventRepository accessEvents, DocumentAccessLog accessLog,
                           DocumentViewMapper mapper, ObjectStorageService storage, ConsentService consents,
                           ProfileService profiles, AuditService audit, ApplicationEventPublisher events, Clock clock,
                           CivixProperties props) {
        this.documents = documents;
        this.extractions = extractions;
        this.accessEvents = accessEvents;
        this.accessLog = accessLog;
        this.mapper = mapper;
        this.storage = storage;
        this.consents = consents;
        this.profiles = profiles;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        String sse = props.storage().sse();
        this.encryptionLabel = sse != null && sse.toLowerCase().contains("kms") ? "SSE-KMS" : "SSE-S3";
    }

    @Transactional(readOnly = true)
    public Page<DocumentDto> list(UUID userId, DocumentCategory category, Pageable pageable) {
        Page<Document> page = category == null ? documents.findByUserIdAndDeletedAtIsNull(userId, pageable)
                : documents.findByUserIdAndCategoryAndDeletedAtIsNull(userId, category, pageable);
        Map<UUID, DocumentExtraction> latest = latestExtractions(page.getContent().stream().map(Document::getId).toList());
        return page.map(d -> mapper.toDto(d, latest.get(d.getId())));
    }

    @Transactional
    public DocumentDto upload(UUID userId, MultipartFile file, DocumentCategory category) {
        consents.require(userId, ConsentPurpose.DOCUMENT_PROCESSING);
        if (category == null) {
            throw ApiException.validation("category", "is required");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.validation("file", "must not be empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ApiException("FILE_TOO_LARGE", HttpStatus.PAYLOAD_TOO_LARGE, "The file exceeds the 10 MB limit");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw ApiException.validation("file", "could not be read");
        }
        if (content.length == 0 || content.length > MAX_SIZE_BYTES) {
            throw ApiException.validation("file", "must be between 1 byte and 10 MB");
        }
        String mimeType = FileTypeDetector.detect(content).orElseThrow(() -> new ApiException("UNSUPPORTED_FILE_TYPE",
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PDF, PNG and JPEG files are accepted"));

        Instant now = clock.instant();
        UUID documentId = UUID.randomUUID();
        String key = Document.storageKeyFor(userId, documentId);
        Document doc = documents.save(new Document(documentId, userId, sanitizeFileName(file.getOriginalFilename(), mimeType),
                category, mimeType, content.length, Hashing.sha256Hex(content), key, encryptionLabel, now));

        // Remove the object again if the transaction does not commit.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        storage.delete(key);
                    } catch (RuntimeException ignored) {
                        // best effort; the key is unguessable and never referenced
                    }
                }
            }
        });
        storage.put(key, content, mimeType);

        accessLog.record(documentId, userId, AccessActorType.USER, userId.toString(), null, AccessAction.UPLOAD, "UPLOAD");
        audit.record(AuditEntry.of(AuditCategory.DOCUMENT, "DOCUMENT_UPLOADED").user(userId)
                .resource("DOCUMENT", documentId)
                .summary("Uploaded a " + category.name() + " document")
                .meta("category", category.name()).meta("mimeType", mimeType).meta("sizeBytes", content.length));
        events.publishEvent(new CitizenDataChangedEvent(userId, "DOCUMENT_UPLOADED"));
        events.publishEvent(new DocumentExtractionRequested(documentId, userId));
        return mapper.toDto(doc, null);
    }

    @Transactional
    public DocumentDto get(UUID userId, UUID documentId) {
        Document doc = owned(userId, documentId);
        accessLog.record(documentId, userId, AccessActorType.USER, userId.toString(), null, AccessAction.VIEW, "VIEW");
        return mapper.toDto(doc, extractions.findFirstByDocumentIdOrderByAttemptDesc(documentId).orElse(null));
    }

    /** An open stream of the document content plus the metadata needed for the response. */
    public record DocumentContent(java.io.InputStream stream, String fileName, String mimeType, long sizeBytes) {
    }

    @Transactional
    public DocumentContent content(UUID userId, UUID documentId) {
        Document doc = owned(userId, documentId);
        ObjectStorageService.StoredObject object = storage.get(doc.getStorageKey())
                .orElseThrow(() -> new ApiException("DOCUMENT_CONTENT_MISSING", HttpStatus.NOT_FOUND,
                        "The stored file could not be found"));
        accessLog.record(documentId, userId, AccessActorType.USER, userId.toString(), null, AccessAction.DOWNLOAD, "DOWNLOAD");
        audit.record(AuditEntry.of(AuditCategory.DOCUMENT, "DOCUMENT_DOWNLOADED").user(userId)
                .resource("DOCUMENT", documentId).summary("Downloaded a document"));
        return new DocumentContent(object.content(), doc.getFileName(), doc.getMimeType(), doc.getSizeBytes());
    }

    @Transactional
    public void delete(UUID userId, UUID documentId) {
        Document doc = documents.findActiveForUpdate(documentId, userId).orElseThrow(() -> ApiException.notFound("Document"));
        Instant now = clock.instant();
        storage.delete(doc.getStorageKey());
        doc.softDelete(now);
        documents.save(doc);
        profiles.detachDocument(documentId);
        accessLog.record(documentId, userId, AccessActorType.USER, userId.toString(), null, AccessAction.DELETE, "DELETE");
        audit.record(AuditEntry.of(AuditCategory.DOCUMENT, "DOCUMENT_DELETED").user(userId)
                .resource("DOCUMENT", documentId).summary("Deleted a " + doc.getCategory().name() + " document"));
        events.publishEvent(new CitizenDataChangedEvent(userId, "DOCUMENT_DELETED"));
    }

    @Transactional
    public DocumentDto reextract(UUID userId, UUID documentId) {
        consents.require(userId, ConsentPurpose.DOCUMENT_PROCESSING);
        Document doc = documents.findActiveForUpdate(documentId, userId).orElseThrow(() -> ApiException.notFound("Document"));
        DocumentExtraction latest = extractions.findFirstByDocumentIdOrderByAttemptDesc(documentId).orElse(null);
        if (isActivelyProcessing(doc, latest)) {
            throw ApiException.conflict("EXTRACTION_IN_PROGRESS", "An extraction for this document is already running");
        }
        doc.markExtraction(ExtractionStatus.PENDING, clock.instant());
        documents.save(doc);
        audit.record(AuditEntry.of(AuditCategory.DOCUMENT, "DOCUMENT_EXTRACTION_REQUESTED").user(userId)
                .resource("DOCUMENT", documentId).summary("Requested a new extraction"));
        events.publishEvent(new DocumentExtractionRequested(documentId, userId));
        return mapper.toDto(doc, latest);
    }

    boolean isActivelyProcessing(Document doc, DocumentExtraction latest) {
        if (doc.getExtractionStatus() != ExtractionStatus.PROCESSING) {
            return false;
        }
        return latest == null || latest.getStartedAt().isAfter(clock.instant().minus(STALE_PROCESSING));
    }

    @Transactional
    public DocumentDto review(UUID userId, UUID documentId, ReviewRequest request) {
        Document doc = documents.findActiveForUpdate(documentId, userId).orElseThrow(() -> ApiException.notFound("Document"));
        DocumentExtraction latest = extractions.findFirstByDocumentIdOrderByAttemptDesc(documentId)
                .filter(e -> e.getStatus() == ExtractionStatus.COMPLETED)
                .orElseThrow(() -> ApiException.conflict("EXTRACTION_NOT_COMPLETED",
                        "There is no completed extraction to review for this document"));

        Map<String, Map<String, Object>> extractedByProfileField = new LinkedHashMap<>();
        Set<String> extractedKeys = new HashSet<>();
        for (Map<String, Object> f : latest.getFields() == null ? List.<Map<String, Object>>of() : latest.getFields()) {
            if (f.get("key") != null) {
                extractedKeys.add(f.get("key").toString());
            }
            if (f.get("profileField") != null) {
                extractedByProfileField.putIfAbsent(f.get("profileField").toString(), f);
            }
        }

        List<ApiException.FieldError> errors = new ArrayList<>();
        List<ProfileService.AcceptedValue> accepted = new ArrayList<>();
        Set<String> acceptedKeys = new HashSet<>();
        List<ReviewRequest.AcceptedField> accept = request.accept() == null ? List.of() : request.accept();
        for (int i = 0; i < accept.size(); i++) {
            ReviewRequest.AcceptedField a = accept.get(i);
            Map<String, Object> extracted = extractedByProfileField.get(a.key());
            if (extracted == null) {
                errors.add(new ApiException.FieldError("accept[" + i + "].key", "was not extracted from this document"));
                continue;
            }
            if (!acceptedKeys.add(a.key())) {
                errors.add(new ApiException.FieldError("accept[" + i + "].key", "is listed more than once"));
                continue;
            }
            Object value = a.value() != null ? a.value() : extracted.get("value");
            accepted.add(new ProfileService.AcceptedValue(a.key(), value, DocumentViewMapper.decimal(extracted.get("confidence"))));
        }
        List<String> reject = request.reject() == null ? List.of() : request.reject();
        for (int i = 0; i < reject.size(); i++) {
            String key = reject.get(i);
            if (!extractedKeys.contains(key) && !extractedByProfileField.containsKey(key)) {
                errors.add(new ApiException.FieldError("reject[" + i + "]", "was not extracted from this document"));
            } else if (acceptedKeys.contains(key)) {
                errors.add(new ApiException.FieldError("reject[" + i + "]", "cannot be both accepted and rejected"));
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }

        List<String> changed = profiles.applyReviewedDocumentValues(userId, documentId, accepted, "accept");
        Instant now = clock.instant();
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("accepted", new ArrayList<>(acceptedKeys));
        review.put("rejected", new ArrayList<>(reject));
        review.put("reviewedAt", now.toString());
        latest.recordReview(review);
        extractions.save(latest);
        doc.markReviewed(now);
        documents.save(doc);
        audit.record(AuditEntry.of(AuditCategory.DOCUMENT, "DOCUMENT_REVIEWED").user(userId)
                .resource("DOCUMENT", documentId)
                .summary("Reviewed extracted values: " + changed.size() + " accepted, " + reject.size() + " rejected")
                .meta("acceptedKeys", changed).meta("rejectedKeys", reject));
        if (changed.isEmpty()) {
            // Reviewing without accepting still changes what the agent may rely on (document reviewed flag).
            events.publishEvent(new CitizenDataChangedEvent(userId, "DOCUMENT_REVIEWED"));
        }
        return mapper.toDto(doc, latest);
    }

    @Transactional(readOnly = true)
    public List<DocumentAccessDto> accessLog(UUID userId, UUID documentId) {
        documents.findById(documentId).filter(d -> d.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Document"));
        return accessEvents.findTop200ByDocumentIdAndUserIdOrderByOccurredAtDesc(documentId, userId).stream()
                .map(DocumentAccessDto::from).toList();
    }

    private Document owned(UUID userId, UUID documentId) {
        return documents.findByIdAndUserIdAndDeletedAtIsNull(documentId, userId)
                .orElseThrow(() -> ApiException.notFound("Document"));
    }

    private Map<UUID, DocumentExtraction> latestExtractions(List<UUID> ids) {
        Map<UUID, DocumentExtraction> map = new HashMap<>();
        if (!ids.isEmpty()) {
            extractions.findLatestFor(ids).forEach(e -> map.put(e.getDocumentId(), e));
        }
        return map;
    }

    /** Keeps a display-safe base name: no paths, no control characters, at most 255 characters. */
    static String sanitizeFileName(String original, String mimeType) {
        String name = original == null ? "" : original;
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder();
        name.codePoints()
                .filter(cp -> !Character.isISOControl(cp) && cp != '"' && cp != '<' && cp != '>')
                .forEach(sb::appendCodePoint);
        String cleaned = sb.toString().strip();
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) {
            cleaned = "document" + FileTypeDetector.extensionFor(mimeType);
        }
        if (cleaned.length() > 255) {
            cleaned = cleaned.substring(cleaned.length() - 255);
        }
        return cleaned;
    }
}
