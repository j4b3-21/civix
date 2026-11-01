package com.civix.applications.domain;

import java.time.Instant;
import java.util.Map;
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

import com.civix.eligibility.domain.EligibilityOutcome;

@Entity
@Table(name = "applications")
public class Application {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "service_code", nullable = false, length = 80)
    private String serviceCode;

    @Column(name = "service_version", nullable = false)
    private int serviceVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private ApplicationStatus status;

    @Column(name = "data_version", nullable = false)
    private int dataVersion;

    @Column(name = "reference_number", length = 128)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility_outcome", length = 24)
    private EligibilityOutcome eligibilityOutcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_action")
    private Map<String, Object> requiredAction;

    @Column(name = "is_test_environment", nullable = false)
    private boolean testEnvironment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Application() {
    }

    public Application(UUID userId, UUID serviceId, String serviceCode, int serviceVersion,
                       EligibilityOutcome eligibilityOutcome, boolean testEnvironment, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.serviceId = serviceId;
        this.serviceCode = serviceCode;
        this.serviceVersion = serviceVersion;
        this.status = ApplicationStatus.DRAFT;
        this.dataVersion = 1;
        this.eligibilityOutcome = eligibilityOutcome;
        this.testEnvironment = testEnvironment;
        this.createdAt = now;
        this.updatedAt = now;
        this.lastActivityAt = now;
    }

    /** Applies an already-validated status change. */
    public void changeStatus(ApplicationStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
        this.lastActivityAt = now;
    }

    public void setRequiredAction(Map<String, Object> requiredAction, Instant now) {
        this.requiredAction = requiredAction;
        this.updatedAt = now;
    }

    public void markSubmitted(String referenceNumber, boolean testEnvironment, Instant now) {
        this.referenceNumber = referenceNumber;
        this.submittedAt = now;
        this.testEnvironment = this.testEnvironment || testEnvironment;
        this.requiredAction = null;
        this.updatedAt = now;
        this.lastActivityAt = now;
    }

    /** Citizen data changed: anything approved against the previous data must be re-approved. */
    public void bumpDataVersion(Instant now) {
        this.dataVersion++;
        this.updatedAt = now;
    }

    public void touchActivity(Instant now) {
        this.lastActivityAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public int getServiceVersion() {
        return serviceVersion;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public int getDataVersion() {
        return dataVersion;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public EligibilityOutcome getEligibilityOutcome() {
        return eligibilityOutcome;
    }

    public Map<String, Object> getRequiredAction() {
        return requiredAction;
    }

    public boolean isTestEnvironment() {
        return testEnvironment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
