package com.civix.approvals.repository;

import java.time.Instant;
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

import com.civix.approvals.domain.Approval;
import com.civix.approvals.domain.ApprovalKind;
import com.civix.approvals.domain.ApprovalStatus;

public interface ApprovalRepository extends JpaRepository<Approval, UUID> {

    Optional<Approval> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Approval a where a.id = :id and a.userId = :userId")
    Optional<Approval> findOwnedForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    Page<Approval> findByUserId(UUID userId, Pageable pageable);

    Page<Approval> findByUserIdAndStatus(UUID userId, ApprovalStatus status, Pageable pageable);

    List<Approval> findByTaskIdAndKindAndStatus(UUID taskId, ApprovalKind kind, ApprovalStatus status);

    List<Approval> findByTaskIdAndStatusIn(UUID taskId, Collection<ApprovalStatus> statuses);

    Optional<Approval> findFirstByTaskIdAndStatusOrderByCreatedAtDesc(UUID taskId, ApprovalStatus status);

    Optional<Approval> findByIdAndTaskId(UUID id, UUID taskId);

    List<Approval> findByUserIdAndStatusAndExpiresAtAfterOrderByCreatedAtDesc(UUID userId, ApprovalStatus status,
                                                                              Instant now, Pageable pageable);

    @Query("""
            select a from Approval a
            where a.userId = :userId and a.applicationId in :applicationIds and a.status in :statuses
            """)
    List<Approval> findOutstandingForApplications(@Param("userId") UUID userId,
                                                  @Param("applicationIds") Collection<UUID> applicationIds,
                                                  @Param("statuses") Collection<ApprovalStatus> statuses);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Approval a set a.status = com.civix.approvals.domain.ApprovalStatus.EXPIRED, a.version = a.version + 1
            where a.status = com.civix.approvals.domain.ApprovalStatus.PENDING and a.expiresAt <= :now
            """)
    int expirePending(@Param("now") Instant now);
}
