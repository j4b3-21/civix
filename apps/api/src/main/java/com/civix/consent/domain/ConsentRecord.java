package com.civix.consent.domain;

import java.time.Instant;
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

/** Append-only; the latest record per (user, purpose) is authoritative. */
@Entity
@Table(name = "consent_records")
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private ConsentPurpose purpose;

    @Column(name = "granted", nullable = false)
    private boolean granted;

    @Column(name = "notice_version", nullable = false, length = 16)
    private String noticeVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope", nullable = false)
    private Map<String, Object> scope;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ConsentRecord() {
    }

    public ConsentRecord(UUID userId, ConsentPurpose purpose, boolean granted, String noticeVersion, Instant now) {
        this.userId = userId;
        this.purpose = purpose;
        this.granted = granted;
        this.noticeVersion = noticeVersion;
        this.scope = Map.of();
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public ConsentPurpose getPurpose() {
        return purpose;
    }

    public boolean isGranted() {
        return granted;
    }

    public String getNoticeVersion() {
        return noticeVersion;
    }

    public Map<String, Object> getScope() {
        return scope;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
