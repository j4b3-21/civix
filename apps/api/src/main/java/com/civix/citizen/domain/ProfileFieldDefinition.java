package com.civix.citizen.domain;

import java.time.Instant;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Profile field catalog entry, synced from the agent registry ({@code service-registry/profile-fields.yaml}). */
@Entity
@Table(name = "profile_field_definitions")
public class ProfileFieldDefinition {

    @Id
    @Column(name = "field_key", length = 64)
    private String fieldKey;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 16)
    private ProfileFieldType fieldType;

    @Column(name = "field_group", nullable = false, length = 32)
    private String fieldGroup;

    @Column(name = "purpose", nullable = false, length = 500)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensitivity", nullable = false, length = 8)
    private Sensitivity sensitivity;

    @Column(name = "core", nullable = false)
    private boolean core;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "definition", nullable = false)
    private Map<String, Object> definition;

    @Column(name = "catalog_version", nullable = false, length = 32)
    private String catalogVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProfileFieldDefinition() {
    }

    public ProfileFieldDefinition(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public void update(String label, ProfileFieldType type, String group, String purpose, Sensitivity sensitivity,
                       boolean core, int displayOrder, Map<String, Object> definition, String catalogVersion, Instant now) {
        this.label = label;
        this.fieldType = type;
        this.fieldGroup = group;
        this.purpose = purpose;
        this.sensitivity = sensitivity;
        this.core = core;
        this.displayOrder = displayOrder;
        this.definition = definition;
        this.catalogVersion = catalogVersion;
        this.updatedAt = now;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public String getLabel() {
        return label;
    }

    public ProfileFieldType getFieldType() {
        return fieldType;
    }

    public String getFieldGroup() {
        return fieldGroup;
    }

    public String getPurpose() {
        return purpose;
    }

    public Sensitivity getSensitivity() {
        return sensitivity;
    }

    public boolean isCore() {
        return core;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public Map<String, Object> getDefinition() {
        return definition;
    }

    public String getCatalogVersion() {
        return catalogVersion;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
