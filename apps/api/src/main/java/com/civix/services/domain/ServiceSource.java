package com.civix.services.domain;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "service_sources")
public class ServiceSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;

    @Column(name = "url", nullable = false, length = 1000)
    private String url;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 24)
    private SourceKind kind;

    @Column(name = "retrieved_at", nullable = false)
    private LocalDate retrievedAt;

    protected ServiceSource() {
    }

    public ServiceSource(UUID serviceId, String url, String title, SourceKind kind, LocalDate retrievedAt) {
        this.serviceId = serviceId;
        this.url = url;
        this.title = title;
        this.kind = kind;
        this.retrievedAt = retrievedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getServiceId() {
        return serviceId;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public SourceKind getKind() {
        return kind;
    }

    public LocalDate getRetrievedAt() {
        return retrievedAt;
    }
}
