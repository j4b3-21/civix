package com.civix.documents.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civix.documents.domain.DocumentAccessEvent;

public interface DocumentAccessEventRepository extends JpaRepository<DocumentAccessEvent, UUID> {

    List<DocumentAccessEvent> findTop200ByDocumentIdAndUserIdOrderByOccurredAtDesc(UUID documentId, UUID userId);
}
