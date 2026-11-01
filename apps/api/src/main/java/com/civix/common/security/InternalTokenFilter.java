package com.civix.common.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.civix.common.crypto.Hashing;

/**
 * Authenticates agent → API calls on {@code /internal/**} with {@code Authorization: Bearer ${API_INTERNAL_TOKEN}}
 * (constant-time comparison). Instantiated inside the internal security chain only.
 */
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String PRINCIPAL = "civix-agent";

    private final String expectedToken;
    private final SecurityProblemWriter problems;

    public InternalTokenFilter(String expectedToken, SecurityProblemWriter problems) {
        if (expectedToken == null || expectedToken.length() < 16) {
            throw new IllegalStateException("API_INTERNAL_TOKEN must be set (at least 16 characters)");
        }
        this.expectedToken = expectedToken;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String presented = header != null && header.regionMatches(true, 0, "Bearer ", 0, 7) ? header.substring(7).trim() : null;
        if (!Hashing.constantTimeEquals(presented, expectedToken)) {
            SecurityContextHolder.clearContext();
            problems.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "A valid internal service token is required");
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                PRINCIPAL, null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
