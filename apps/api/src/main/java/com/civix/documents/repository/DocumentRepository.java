package com.civix.documents.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civix.documents.domain.Document;
import com.civix.documents.domain.DocumentCategory;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :id and d.userId = :userId and d.deletedAt is null")
    Optional<Document> findActiveForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    Page<Document> findByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);

    Page<Document> findByUserIdAndCategoryAndDeletedAtIsNull(UUID userId, DocumentCategory category, Pageable pageable);

    List<Document> findByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(UUID userId);

    List<Document> findByUserId(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Document d where d.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}
