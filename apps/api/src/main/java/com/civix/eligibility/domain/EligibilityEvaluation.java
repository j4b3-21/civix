package com.civix.eligibility.domain;

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

/** A persisted deterministic eligibility result for (user, service version). */
@Entity
@Table(name = "eligibility_evaluations")
public class EligibilityEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "service_version", nullable = false)
    private int serviceVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 24)
    private EligibilityOutcome outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", nullable = false)
    private Map<String, Object> result;

    @Column(name = "engine_version", nullable = false, length = 16)
    private String engineVersion;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    protected EligibilityEvaluation() {
    }

    public EligibilityEvaluation(UUID userId, UUID serviceId, int serviceVersion, EligibilityOutcome outcome,
                                 Map<String, Object> result, String engineVersion, Instant evaluatedAt) {
        this.userId = userId;
        this.serviceId = serviceId;
        this.serviceVersion = serviceVersion;
        this.outcome = outcome;
        this.result = result;
        this.engineVersion = engineVersion;
        this.evaluatedAt = evaluatedAt;
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

    public int getServiceVersion() {
        return serviceVersion;
    }

    public EligibilityOutcome getOutcome() {
        return outcome;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }
}
