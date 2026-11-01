package com.civix.documents.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A citizen document in private, server-side-encrypted object storage. Soft-deleted via {@code deleted_at}. */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 32)
    private DocumentCategory category;

    @Column(name = "mime_type", nullable = false, length = 64)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, length = 512, unique = true)
    private String storageKey;

    @Column(name = "encryption", nullable = false, length = 32)
    private String encryption;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 16)
    private ExtractionStatus extractionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 16)
    private ReviewStatus reviewStatus;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Document() {
    }

    public Document(UUID id, UUID userId, String fileName, DocumentCategory category, String mimeType, long sizeBytes,
                    String sha256, String storageKey, String encryption, Instant now) {
        this.id = id;
        this.userId = userId;
        this.fileName = fileName;
        this.category = category;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.encryption = encryption;
        this.extractionStatus = ExtractionStatus.PENDING;
        this.reviewStatus = ReviewStatus.NOT_REVIEWED;
        this.uploadedAt = now;
        this.updatedAt = now;
    }

    public static String storageKeyFor(UUID userId, UUID documentId) {
        return "users/" + userId + "/documents/" + documentId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markExtraction(ExtractionStatus status, Instant now) {
        this.extractionStatus = status;
        if (status == ExtractionStatus.PENDING || status == ExtractionStatus.COMPLETED) {
            this.reviewStatus = ReviewStatus.NOT_REVIEWED;
        }
        this.updatedAt = now;
    }

    public void markReviewed(Instant now) {
        this.reviewStatus = ReviewStatus.REVIEWED;
        this.updatedAt = now;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFileName() {
        return fileName;
    }

    public DocumentCategory getCategory() {
        return category;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getEncryption() {
        return encryption;
    }

    public ExtractionStatus getExtractionStatus() {
        return extractionStatus;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
