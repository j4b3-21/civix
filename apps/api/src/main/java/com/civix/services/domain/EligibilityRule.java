package com.civix.services.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.ColumnTransformer;

/** Denormalised eligibility rule of a service version. {@code value} is any JSON value, kept as JSON text. */
@Entity
@Table(name = "eligibility_rules")
public class EligibilityRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "rule_id", nullable = false, length = 64)
    private String ruleId;

    @Column(name = "rule_version", nullable = false)
    private int ruleVersion;

    @Column(name = "field_key", nullable = false, length = 64)
    private String fieldKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 24)
    private RuleOperator operator;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "value", columnDefinition = "jsonb")
    private String valueJson;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    protected EligibilityRule() {
    }

    public EligibilityRule(UUID serviceId, String ruleId, int ruleVersion, String fieldKey, RuleOperator operator,
                           String valueJson, String description, String sourceUrl) {
        this.serviceId = serviceId;
        this.ruleId = ruleId;
        this.ruleVersion = ruleVersion;
        this.fieldKey = fieldKey;
        this.operator = operator;
        this.valueJson = valueJson;
        this.description = description;
        this.sourceUrl = sourceUrl;
    }

    public UUID getId() {
        return id;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public String getRuleId() {
        return ruleId;
    }

    public int getRuleVersion() {
        return ruleVersion;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public RuleOperator getOperator() {
        return operator;
    }

    public String getValueJson() {
        return valueJson;
    }

    public String getDescription() {
        return description;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }
}
