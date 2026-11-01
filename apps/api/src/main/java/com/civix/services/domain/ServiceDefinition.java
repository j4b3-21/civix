package com.civix.services.domain;

import java.time.Instant;
import java.time.LocalDate;
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

/**
 * A synced, validated government service definition (one row per code + version). The generated
 * {@code search_vector} and optional {@code embedding} columns are intentionally not mapped.
 */
@Entity
@Table(name = "service_definitions")
public class ServiceDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    /** Registry version of the definition (not an optimistic-lock column). */
    @Column(name = "version", nullable = false)
    private int definitionVersion;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "department", nullable = false, length = 255)
    private String department;

    @Column(name = "ministry", length = 255)
    private String ministry;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 16)
    private ServiceCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "jurisdiction_level", nullable = false, length = 8)
    private JurisdictionLevel jurisdictionLevel;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "state_codes", nullable = false, columnDefinition = "text[]")
    private String[] stateCodes;

    @Column(name = "official_url", nullable = false, length = 500)
    private String officialUrl;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "portal_domains", nullable = false, columnDefinition = "text[]")
    private String[] portalDomains;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private ServiceVerificationStatus verificationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "automation_status", nullable = false, length = 16)
    private AutomationStatus automationStatus;

    @Column(name = "is_test_service", nullable = false)
    private boolean testService;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "deadline")
    private Map<String, Object> deadline;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition", nullable = false)
    private Map<String, Object> definition;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "last_verified_at")
    private LocalDate lastVerifiedAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ServiceDefinition() {
    }

    public ServiceDefinition(String code, int definitionVersion, Instant now) {
        this.code = code;
        this.definitionVersion = definitionVersion;
        this.createdAt = now;
        this.updatedAt = now;
        this.active = true;
    }

    /** Applies the synced content. */
    public void apply(ServiceContent c, Map<String, Object> definition, String contentHash, Instant now) {
        this.name = c.name();
        this.summary = c.summary();
        this.description = c.description();
        this.department = c.department();
        this.ministry = c.ministry();
        this.category = c.category();
        this.jurisdictionLevel = c.jurisdictionLevel();
        this.stateCodes = c.stateCodes();
        this.officialUrl = c.officialUrl();
        this.portalDomains = c.portalDomains();
        this.verificationStatus = c.verificationStatus();
        this.automationStatus = c.automationStatus();
        this.testService = c.testService();
        this.deadline = c.deadline();
        this.lastVerifiedAt = c.lastVerifiedAt();
        this.definition = definition;
        this.contentHash = contentHash;
        this.updatedAt = now;
    }

    public void setActive(boolean active, Instant now) {
        if (this.active != active) {
            this.active = active;
            this.updatedAt = now;
        }
    }

    /** Typed content of a definition, as validated by the registry sync. */
    public record ServiceContent(String name, String summary, String description, String department, String ministry,
                                 ServiceCategory category, JurisdictionLevel jurisdictionLevel, String[] stateCodes,
                                 String officialUrl, String[] portalDomains,
                                 ServiceVerificationStatus verificationStatus, AutomationStatus automationStatus,
                                 boolean testService, Map<String, Object> deadline, LocalDate lastVerifiedAt) {
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public int getDefinitionVersion() {
        return definitionVersion;
    }

    public String getName() {
        return name;
    }

    public String getSummary() {
        return summary;
    }

    public String getDescription() {
        return description;
    }

    public String getDepartment() {
        return department;
    }

    public String getMinistry() {
        return ministry;
    }

    public ServiceCategory getCategory() {
        return category;
    }

    public JurisdictionLevel getJurisdictionLevel() {
        return jurisdictionLevel;
    }

    public String[] getStateCodes() {
        return stateCodes == null ? new String[0] : stateCodes.clone();
    }

    public String getOfficialUrl() {
        return officialUrl;
    }

    public String[] getPortalDomains() {
        return portalDomains == null ? new String[0] : portalDomains.clone();
    }

    public ServiceVerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public AutomationStatus getAutomationStatus() {
        return automationStatus;
    }

    public boolean isTestService() {
        return testService;
    }

    public Map<String, Object> getDeadline() {
        return deadline;
    }

    public Map<String, Object> getDefinition() {
        return definition;
    }

    public String getContentHash() {
        return contentHash;
    }

    public LocalDate getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
