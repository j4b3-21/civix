package com.civix.applications.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.civix.agent.domain.TaskStatus;

/**
 * The status machines of docs/WORKFLOWS.md §2–3, enforced by Spring for every change (agent callbacks, citizen
 * actions, receipts). Same-state "transitions" are no-ops and always allowed.
 */
public final class TransitionPolicy {

    private static final Map<TaskStatus, Set<TaskStatus>> TASK = new EnumMap<>(TaskStatus.class);
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> APPLICATION = new EnumMap<>(ApplicationStatus.class);

    static {
        TASK.put(TaskStatus.QUEUED, EnumSet.of(TaskStatus.RUNNING, TaskStatus.CANCELLED, TaskStatus.FAILED));
        TASK.put(TaskStatus.RUNNING, EnumSet.of(TaskStatus.WAITING_APPROVAL, TaskStatus.WAITING_CITIZEN,
                TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED));
        TASK.put(TaskStatus.WAITING_APPROVAL, EnumSet.of(TaskStatus.RUNNING, TaskStatus.CANCELLED, TaskStatus.FAILED));
        TASK.put(TaskStatus.WAITING_CITIZEN, EnumSet.of(TaskStatus.RUNNING, TaskStatus.CANCELLED, TaskStatus.FAILED));
        TASK.put(TaskStatus.FAILED, EnumSet.of(TaskStatus.QUEUED));
        TASK.put(TaskStatus.COMPLETED, EnumSet.noneOf(TaskStatus.class));
        TASK.put(TaskStatus.CANCELLED, EnumSet.noneOf(TaskStatus.class));

        APPLICATION.put(ApplicationStatus.DRAFT, EnumSet.of(ApplicationStatus.IN_PROGRESS, ApplicationStatus.CANCELLED));
        APPLICATION.put(ApplicationStatus.IN_PROGRESS, EnumSet.of(ApplicationStatus.ACTION_REQUIRED,
                ApplicationStatus.SUBMITTED, ApplicationStatus.SUBMISSION_UNCERTAIN, ApplicationStatus.FAILED,
                ApplicationStatus.CANCELLED));
        APPLICATION.put(ApplicationStatus.ACTION_REQUIRED, EnumSet.of(ApplicationStatus.IN_PROGRESS,
                ApplicationStatus.CANCELLED, ApplicationStatus.FAILED, ApplicationStatus.DRAFT));
        APPLICATION.put(ApplicationStatus.SUBMISSION_UNCERTAIN, EnumSet.of(ApplicationStatus.SUBMITTED,
                ApplicationStatus.FAILED));
        APPLICATION.put(ApplicationStatus.FAILED, EnumSet.of(ApplicationStatus.IN_PROGRESS, ApplicationStatus.CANCELLED));
        APPLICATION.put(ApplicationStatus.SUBMITTED, EnumSet.noneOf(ApplicationStatus.class));
        APPLICATION.put(ApplicationStatus.CANCELLED, EnumSet.noneOf(ApplicationStatus.class));
    }

    private TransitionPolicy() {
    }

    public static boolean canTransition(TaskStatus from, TaskStatus to) {
        return from == to || TASK.get(from).contains(to);
    }

    /**
     * {@code FAILED → QUEUED} is a retry and is only allowed when the failure was marked retryable.
     */
    public static boolean canTransition(TaskStatus from, TaskStatus to, boolean errorRetryable) {
        if (from == TaskStatus.FAILED && to == TaskStatus.QUEUED) {
            return errorRetryable;
        }
        return canTransition(from, to);
    }

    public static boolean canTransition(ApplicationStatus from, ApplicationStatus to) {
        return from == to || APPLICATION.get(from).contains(to);
    }

    public static Set<TaskStatus> allowedFrom(TaskStatus from) {
        return EnumSet.copyOf(TASK.get(from).isEmpty() ? EnumSet.noneOf(TaskStatus.class) : TASK.get(from));
    }

    public static Set<ApplicationStatus> allowedFrom(ApplicationStatus from) {
        Set<ApplicationStatus> allowed = APPLICATION.get(from);
        return allowed.isEmpty() ? EnumSet.noneOf(ApplicationStatus.class) : EnumSet.copyOf(allowed);
    }
}
