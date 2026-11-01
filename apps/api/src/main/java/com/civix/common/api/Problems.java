package com.civix.common.api;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import com.civix.common.web.RequestCorrelation;

/** Builds RFC 9457 problem documents with the CIVIX extension members {@code code}, {@code correlationId}, {@code errors}. */
public final class Problems {

    public static final String TYPE_PREFIX = "https://civix.dev/problems/";

    private Problems() {
    }

    public static ProblemDetail problem(HttpStatusCode status, String code, String detail,
                                        List<ApiException.FieldError> errors, Map<String, Object> extra) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setType(URI.create(TYPE_PREFIX + code));
        pd.setTitle(title(status));
        pd.setDetail(detail);
        pd.setProperty("code", code);
        pd.setProperty("correlationId", RequestCorrelation.currentIdOrNull());
        if (errors != null && !errors.isEmpty()) {
            pd.setProperty("errors", errors);
        }
        if (extra != null) {
            extra.forEach(pd::setProperty);
        }
        return pd;
    }

    /** Same document as a plain map, for writers that bypass Spring MVC message conversion (security filters). */
    public static Map<String, Object> asMap(HttpStatusCode status, String code, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", TYPE_PREFIX + code);
        body.put("title", title(status));
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("code", code);
        body.put("correlationId", RequestCorrelation.currentIdOrNull());
        return body;
    }

    public static String title(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "Error";
    }
}
