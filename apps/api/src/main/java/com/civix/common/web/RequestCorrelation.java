package com.civix.common.web;

import java.util.Optional;

import org.slf4j.MDC;

/** Access to the correlation id of the current request (set by {@link RequestCorrelationFilter}). */
public final class RequestCorrelation {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private RequestCorrelation() {
    }

    public static Optional<String> currentId() {
        return Optional.ofNullable(MDC.get(MDC_KEY));
    }

    public static String currentIdOrNull() {
        return MDC.get(MDC_KEY);
    }
}
