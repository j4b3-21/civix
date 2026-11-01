package com.civix.common.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps every error to {@code application/problem+json} with {@code code}, {@code correlationId} and, for
 * validation failures, {@code errors[{field, message}]}. Rejected values are never echoed (they may be secrets,
 * e.g. an OTP sent to {@code /citizen-input}).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof RateLimitExceededException rl) {
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(rl.retryAfterSeconds()));
        }
        if (ex.status().is5xxServerError()) {
            log.warn("Request failed with {}: {}", ex.code(), ex.getMessage());
        }
        ProblemDetail pd = Problems.problem(ex.status(), ex.code(), ex.getMessage(), ex.fieldErrors(), ex.properties());
        return ResponseEntity.status(ex.status()).headers(headers).body(pd);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiException.FieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new ApiException.FieldError(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(Problems.problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "The request contains invalid values", errors, null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
        log.info("Data integrity violation: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Problems.problem(HttpStatus.CONFLICT, "CONFLICT",
                "The request conflicts with the current state of the resource", null, null));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Problems.problem(HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION", "The resource was modified concurrently; reload and try again", null, null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Problems.problem(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You are not allowed to perform this action", null, null));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Problems.problem(HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED", "Authentication is required", null, null));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ProblemDetail> handleMultipart(MultipartException ex) {
        return ResponseEntity.badRequest().body(Problems.problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "The multipart request could not be processed", null, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Problems.problem(
                HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", null, null));
    }

    // ---- Spring MVC standard exceptions ----------------------------------------------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<ApiException.FieldError> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.add(new ApiException.FieldError(fe.getField(), safeMessage(fe.getDefaultMessage()))));
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.add(new ApiException.FieldError(ge.getObjectName(), safeMessage(ge.getDefaultMessage()))));
        ProblemDetail pd = Problems.problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "The request contains invalid values", errors, null);
        return ResponseEntity.badRequest().headers(headers).body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        String code = codeFor(ex, statusCode);
        String detail = detailFor(ex, statusCode);
        ProblemDetail pd = Problems.problem(statusCode, code, detail, null, Map.of());
        return ResponseEntity.status(statusCode).headers(headers).body(pd);
    }

    private static String codeFor(Exception ex, HttpStatusCode status) {
        if (ex instanceof MaxUploadSizeExceededException) {
            return "FILE_TOO_LARGE";
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "MALFORMED_REQUEST";
        }
        if (ex instanceof MissingServletRequestParameterException || ex instanceof MissingServletRequestPartException
                || ex instanceof MissingRequestHeaderException || ex instanceof MethodArgumentTypeMismatchException
                || ex instanceof HandlerMethodValidationException || ex instanceof MultipartException) {
            return "VALIDATION_FAILED";
        }
        if (ex instanceof NoResourceFoundException) {
            return "NOT_FOUND";
        }
        if (ex instanceof HttpRequestMethodNotSupportedException) {
            return "METHOD_NOT_ALLOWED";
        }
        if (ex instanceof HttpMediaTypeNotSupportedException) {
            return "UNSUPPORTED_MEDIA_TYPE";
        }
        if (ex instanceof HttpMediaTypeNotAcceptableException) {
            return "NOT_ACCEPTABLE";
        }
        return switch (status.value()) {
            case 400 -> "VALIDATION_FAILED";
            case 404 -> "NOT_FOUND";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 503 -> "SERVICE_UNAVAILABLE";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_REJECTED";
        };
    }

    private static String detailFor(Exception ex, HttpStatusCode status) {
        if (ex instanceof MaxUploadSizeExceededException) {
            return "The file exceeds the 10 MB limit";
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "The request body is missing or is not valid JSON of the expected shape";
        }
        if (ex instanceof MissingServletRequestParameterException m) {
            return "Missing required parameter '" + m.getParameterName() + "'";
        }
        if (ex instanceof MissingServletRequestPartException m) {
            return "Missing required part '" + m.getRequestPartName() + "'";
        }
        if (ex instanceof MethodArgumentTypeMismatchException m) {
            return "Parameter '" + m.getName() + "' has an invalid value";
        }
        if (ex instanceof NoResourceFoundException) {
            return "Resource not found";
        }
        return Problems.title(status);
    }

    private static String safeMessage(String message) {
        return message == null ? "is invalid" : message;
    }

    private static String lastNode(String path) {
        int idx = path.lastIndexOf('.');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
