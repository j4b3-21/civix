package com.civix.agent.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.civix.agent.domain.AgentCheckpoint;

public interface AgentCheckpointRepository extends JpaRepository<AgentCheckpoint, UUID> {

    boolean existsByTaskIdAndCheckpointId(UUID taskId, String checkpointId);
}
