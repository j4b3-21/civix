package com.civix.common.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Business error rendered as RFC 9457 problem+json with a stable machine-readable {@code code}.
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final HttpStatus status;
    private final List<FieldError> fieldErrors;
    private final Map<String, Object> properties;

    public ApiException(String code, HttpStatus status, String detail) {
        this(code, status, detail, List.of(), Map.of());
    }

    public ApiException(String code, HttpStatus status, String detail, List<FieldError> fieldErrors) {
        this(code, status, detail, fieldErrors, Map.of());
    }

    public ApiException(String code, HttpStatus status, String detail, List<FieldError> fieldErrors,
                        Map<String, Object> properties) {
        super(detail);
        this.code = code;
        this.status = status;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
        this.properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public List<FieldError> fieldErrors() {
        return fieldErrors;
    }

    public Map<String, Object> properties() {
        return properties;
    }

    /** A single field validation error ({@code errors[]} entry of the problem document). */
    public record FieldError(String field, String message) {
    }

    // ---- common factories ------------------------------------------------------------------------

    public static ApiException notFound(String what) {
        return new ApiException("NOT_FOUND", HttpStatus.NOT_FOUND, what + " not found");
    }

    public static ApiException validation(List<FieldError> errors) {
        return new ApiException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "The request contains invalid values", errors);
    }

    public static ApiException validation(String field, String message) {
        return validation(List.of(new FieldError(field, message)));
    }

    public static ApiException conflict(String code, String detail) {
        return new ApiException(code, HttpStatus.CONFLICT, detail);
    }

    public static ApiException consentRequired(String purpose) {
        return new ApiException("CONSENT_REQUIRED", HttpStatus.FORBIDDEN,
                "This action requires your consent for " + purpose, List.of(), Map.of("purpose", purpose));
    }
}
