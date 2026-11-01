package com.civix.common.web;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Echoes a well-formed {@code X-Request-Id} or generates one, exposes it in the MDC (and therefore in every
 * ECS-structured log line) and on the response. Runs before Spring Security.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._:-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(RequestCorrelation.HEADER);
        String requestId = incoming != null && VALID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        MDC.put(RequestCorrelation.MDC_KEY, requestId);
        response.setHeader(RequestCorrelation.HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestCorrelation.MDC_KEY);
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }
}
