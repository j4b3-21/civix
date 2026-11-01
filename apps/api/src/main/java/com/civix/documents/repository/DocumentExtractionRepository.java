package com.civix.documents.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civix.documents.domain.DocumentExtraction;

public interface DocumentExtractionRepository extends JpaRepository<DocumentExtraction, UUID> {

    Optional<DocumentExtraction> findFirstByDocumentIdOrderByAttemptDesc(UUID documentId);

    @Query("select coalesce(max(e.attempt), 0) from DocumentExtraction e where e.documentId = :documentId")
    int maxAttempt(@Param("documentId") UUID documentId);

    @Query("""
            select e from DocumentExtraction e
            where e.documentId in :documentIds
              and e.attempt = (select max(e2.attempt) from DocumentExtraction e2 where e2.documentId = e.documentId)
            """)
    List<DocumentExtraction> findLatestFor(@Param("documentIds") Collection<UUID> documentIds);
}
