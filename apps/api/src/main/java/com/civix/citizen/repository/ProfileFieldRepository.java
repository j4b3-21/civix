package com.civix.citizen.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civix.citizen.domain.ProfileField;

public interface ProfileFieldRepository extends JpaRepository<ProfileField, UUID> {

    List<ProfileField> findByUserId(UUID userId);

    long countByUserId(UUID userId);

    @Modifying(flushAutomatically = true)
    @Query("update ProfileField f set f.sourceDocumentId = null where f.sourceDocumentId = :documentId")
    int clearSourceDocument(@Param("documentId") UUID documentId);

    @Modifying(flushAutomatically = true)
    @Query("delete from ProfileField f where f.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}
