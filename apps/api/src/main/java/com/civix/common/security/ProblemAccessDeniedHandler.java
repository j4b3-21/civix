package com.civix.common.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

/** 403 problem+json; CSRF failures get their own code so the SPA can refresh its token. */
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityProblemWriter writer;

    public ProblemAccessDeniedHandler(SecurityProblemWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {
        if (ex instanceof CsrfException) {
            writer.write(response, HttpStatus.FORBIDDEN, "CSRF_TOKEN_INVALID",
                    "Missing or invalid CSRF token. Send the XSRF-TOKEN cookie value in the X-XSRF-TOKEN header.");
            return;
        }
        writer.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to perform this action");
    }
}
