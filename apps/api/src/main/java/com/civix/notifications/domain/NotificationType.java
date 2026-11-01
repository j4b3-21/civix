package com.civix.notifications.domain;

/** Notification types (stored as strings; the set may grow without a migration). */
public final class NotificationType {

    public static final String APPROVAL_REQUIRED = "APPROVAL_REQUIRED";
    public static final String SUBMISSION_APPROVAL_REQUIRED = "SUBMISSION_APPROVAL_REQUIRED";
    public static final String CITIZEN_INPUT_REQUIRED = "CITIZEN_INPUT_REQUIRED";
    public static final String RECEIPT_VERIFIED = "RECEIPT_VERIFIED";
    public static final String TASK_FAILED = "TASK_FAILED";
    public static final String SUBMISSION_UNCERTAIN = "SUBMISSION_UNCERTAIN";
    public static final String APPROVAL_INVALIDATED = "APPROVAL_INVALIDATED";

    private NotificationType() {
    }
}
