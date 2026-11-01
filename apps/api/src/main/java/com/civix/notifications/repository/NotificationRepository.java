package com.civix.notifications.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civix.notifications.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserId(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNull(UUID userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndTypeAndLink(UUID userId, String type, String link);

    @Modifying(flushAutomatically = true)
    @Query("delete from Notification n where n.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}
