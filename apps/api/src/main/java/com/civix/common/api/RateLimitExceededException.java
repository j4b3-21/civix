package com.civix.common.api;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends ApiException {

    private static final long serialVersionUID = 1L;

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Try again in " + retryAfterSeconds + " seconds.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
