package com.civix.agent.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only execution timeline. {@code sequence} (identity) is the SSE event id. */
@Entity
@Table(name = "execution_events")
public class ExecutionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sequence")
    private Long sequence;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "agent", nullable = false, length = 32)
    private String agent;

    @Column(name = "step", length = 128)
    private String step;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    private Map<String, Object> metadata;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected ExecutionEvent() {
    }

    public ExecutionEvent(UUID eventId, UUID taskId, String eventType, String agent, String step, String message,
                          Map<String, Object> metadata, Instant occurredAt, Instant receivedAt) {
        this.eventId = eventId;
        this.taskId = taskId;
        this.eventType = eventType;
        this.agent = agent;
        this.step = step;
        this.message = message;
        this.metadata = metadata;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
    }

    public Long getSequence() {
        return sequence;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getAgent() {
        return agent;
    }

    public String getStep() {
        return step;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
