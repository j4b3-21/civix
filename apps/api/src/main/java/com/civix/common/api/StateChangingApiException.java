package com.civix.common.api;

import org.springframework.http.HttpStatus;

/**
 * An {@link ApiException} thrown <em>after</em> a state change that must be committed anyway
 * (e.g. an approval is marked EXPIRED and the caller receives 410, or a failed login increments the
 * failure counter). Service methods that throw it are annotated
 * {@code @Transactional(noRollbackFor = StateChangingApiException.class)}.
 */
public class StateChangingApiException extends ApiException {

    private static final long serialVersionUID = 1L;

    public StateChangingApiException(String code, HttpStatus status, String detail) {
        super(code, status, detail);
    }
}
