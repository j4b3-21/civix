package com.civix.agent.domain;

import java.util.EnumSet;
import java.util.Set;

public enum TaskStatus {
    QUEUED,
    RUNNING,
    WAITING_APPROVAL,
    WAITING_CITIZEN,
    COMPLETED,
    FAILED,
    CANCELLED;

    /** Statuses covered by the "one active task per application" unique index. */
    public static final Set<TaskStatus> ACTIVE = EnumSet.of(QUEUED, RUNNING, WAITING_APPROVAL, WAITING_CITIZEN);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean isEnded() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
