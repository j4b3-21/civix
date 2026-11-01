package com.civix.common.storage;

import org.springframework.http.HttpStatus;

import com.civix.common.api.ApiException;

public class StorageUnavailableException extends ApiException {

    private static final long serialVersionUID = 1L;

    public StorageUnavailableException(String detail, Throwable cause) {
        super("STORAGE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, detail);
        initCause(cause);
    }
}
