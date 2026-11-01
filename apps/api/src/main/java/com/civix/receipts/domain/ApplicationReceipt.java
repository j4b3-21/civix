package com.civix.receipts.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A receipt the Verification Agent actually observed on the portal. Never created from an unobserved claim. */
@Entity
@Table(name = "application_receipts")
public class ApplicationReceipt {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "reference_number", nullable = false, length = 128)
    private String referenceNumber;

    @Column(name = "acknowledgement_text", length = 4000)
    private String acknowledgementText;

    @Column(name = "portal_url", nullable = false, length = 1000)
    private String portalUrl;

    @Column(name = "verification_method", nullable = false, length = 32)
    private String verificationMethod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", nullable = false)
    private Map<String, Object> evidence;

    @Column(name = "artifact_storage_key", length = 512)
    private String artifactStorageKey;

    @Column(name = "is_test_environment", nullable = false)
    private boolean testEnvironment;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ApplicationReceipt() {
    }

    public ApplicationReceipt(UUID applicationId, UUID taskId, String referenceNumber, String acknowledgementText,
                              String portalUrl, String verificationMethod, Map<String, Object> evidence,
                              boolean testEnvironment, Instant now) {
        this.id = UUID.randomUUID();
        this.applicationId = applicationId;
        this.taskId = taskId;
        this.referenceNumber = referenceNumber;
        this.acknowledgementText = acknowledgementText;
        this.portalUrl = portalUrl;
        this.verificationMethod = verificationMethod;
        this.evidence = evidence;
        this.testEnvironment = testEnvironment;
        this.verifiedAt = now;
        this.createdAt = now;
    }

    public void attachArtifact(String storageKey) {
        this.artifactStorageKey = storageKey;
    }

    public UUID getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public UUID getTaskId() { return taskId; }
    public String getReferenceNumber() { return referenceNumber; }
    public String getAcknowledgementText() { return acknowledgementText; }
    public String getPortalUrl() { return portalUrl; }
    public String getVerificationMethod() { return verificationMethod; }
    public Map<String, Object> getEvidence() { return evidence; }
    public String getArtifactStorageKey() { return artifactStorageKey; }
    public boolean isTestEnvironment() { return testEnvironment; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
