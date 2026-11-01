package com.civix.common.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Forces the deferred CSRF token to load so the readable {@code XSRF-TOKEN} cookie is always issued
 * (SPA pattern). Instantiated inside the security chain only; never registered as a servlet filter.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Object attr = request.getAttribute(CsrfToken.class.getName());
        if (attr instanceof CsrfToken token) {
            token.getToken();
        }
        request.setAttribute(CookieBearerTokenResolver.COOKIE_AUTHENTICATION_ALLOWED, Boolean.TRUE);
        chain.doFilter(request, response);
    }
}
