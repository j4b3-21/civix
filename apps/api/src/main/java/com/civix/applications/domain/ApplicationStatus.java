package com.civix.applications.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ApplicationStatus {
    DRAFT,
    IN_PROGRESS,
    ACTION_REQUIRED,
    SUBMITTED,
    SUBMISSION_UNCERTAIN,
    FAILED,
    CANCELLED;

    /** Statuses excluded from the "one open application per service" unique index. */
    public static final Set<ApplicationStatus> CLOSED = EnumSet.of(SUBMITTED, FAILED, CANCELLED);

    public static final Set<ApplicationStatus> OPEN = EnumSet.complementOf(EnumSet.of(SUBMITTED, FAILED, CANCELLED));

    public boolean isOpen() {
        return OPEN.contains(this);
    }
}
