package com.civix.citizen.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civix.citizen.domain.ProfileFieldHistory;

public interface ProfileFieldHistoryRepository extends JpaRepository<ProfileFieldHistory, UUID> {

    @Modifying(flushAutomatically = true)
    @Query("delete from ProfileFieldHistory h where h.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}
