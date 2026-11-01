package com.civix.agent.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Reference to a LangGraph checkpoint (the checkpoint itself lives in schema {@code agent}). */
@Entity
@Table(name = "agent_checkpoints")
public class AgentCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "thread_id", nullable = false, length = 64)
    private String threadId;

    @Column(name = "checkpoint_id", nullable = false, length = 64)
    private String checkpointId;

    @Column(name = "node", length = 64)
    private String node;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected AgentCheckpoint() {
    }

    public AgentCheckpoint(UUID taskId, String threadId, String checkpointId, String node, Instant recordedAt) {
        this.taskId = taskId;
        this.threadId = threadId;
        this.checkpointId = checkpointId;
        this.node = node;
        this.recordedAt = recordedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getThreadId() {
        return threadId;
    }

    public String getCheckpointId() {
        return checkpointId;
    }

    public String getNode() {
        return node;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
