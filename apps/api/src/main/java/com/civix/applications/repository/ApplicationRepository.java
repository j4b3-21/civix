package com.civix.applications.repository;

import java.util.Collection;
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

import com.civix.applications.domain.Application;
import com.civix.applications.domain.ApplicationStatus;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    Optional<Application> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Application a where a.id = :id")
    Optional<Application> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Application a where a.id = :id and a.userId = :userId")
    Optional<Application> findOwnedForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    Page<Application> findByUserId(UUID userId, Pageable pageable);

    Page<Application> findByUserIdAndStatus(UUID userId, ApplicationStatus status, Pageable pageable);

    List<Application> findByUserIdAndStatusInOrderByLastActivityAtDesc(UUID userId, Collection<ApplicationStatus> statuses,
                                                                       Pageable pageable);

    boolean existsByUserIdAndServiceIdAndStatusIn(UUID userId, UUID serviceId, Collection<ApplicationStatus> statuses);

    @Query("select a.id from Application a where a.userId = :userId and a.status in :statuses")
    List<UUID> findIdsByUserAndStatus(@Param("userId") UUID userId,
                                      @Param("statuses") Collection<ApplicationStatus> statuses);

    @Modifying(flushAutomatically = true)
    @Query("delete from Application a where a.userId = :userId")
    int deleteAllForUser(@Param("userId") UUID userId);
}
