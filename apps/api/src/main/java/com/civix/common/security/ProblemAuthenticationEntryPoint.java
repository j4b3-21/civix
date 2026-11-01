package com.civix.common.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** 401 problem+json for missing, expired or invalid credentials. */
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityProblemWriter writer;

    public ProblemAuthenticationEntryPoint(SecurityProblemWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        writer.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
    }
}
