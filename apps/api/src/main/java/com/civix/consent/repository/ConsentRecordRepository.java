package com.civix.consent.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civix.consent.domain.ConsentPurpose;
import com.civix.consent.domain.ConsentRecord;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    List<ConsentRecord> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<ConsentRecord> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(UUID userId, ConsentPurpose purpose);

    @Modifying(flushAutomatically = true)
    @Query("delete from ConsentRecord c where c.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}
