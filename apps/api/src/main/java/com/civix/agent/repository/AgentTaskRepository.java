package com.civix.agent.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.civix.agent.domain.AgentTask;
import com.civix.agent.domain.TaskStatus;

public interface AgentTaskRepository extends JpaRepository<AgentTask, UUID> {

    Optional<AgentTask> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AgentTask t where t.id = :id")
    Optional<AgentTask> findByIdForUpdate(@Param("id") UUID id);

    Page<AgentTask> findByUserId(UUID userId, Pageable pageable);

    Page<AgentTask> findByStatus(TaskStatus status, Pageable pageable);

    List<AgentTask> findByUserIdAndStatusOrderByUpdatedAtDesc(UUID userId, TaskStatus status, Pageable pageable);

    List<AgentTask> findByUserIdAndStatusIn(UUID userId, Collection<TaskStatus> statuses);

    Optional<AgentTask> findFirstByApplicationIdOrderByCreatedAtDesc(UUID applicationId);

    Optional<AgentTask> findFirstByApplicationIdAndStatusIn(UUID applicationId, Collection<TaskStatus> statuses);

    @Query("select t.id from AgentTask t where t.userId = :userId")
    List<UUID> findIdsByUserId(@Param("userId") UUID userId);
}
