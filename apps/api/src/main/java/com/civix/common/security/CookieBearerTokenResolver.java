package com.civix.common.security;

import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Resolves the access token from {@code Authorization: Bearer} (preferred) or the httpOnly {@code civix_at} cookie.
 * Token-less auth endpoints (login/register/refresh/logout) are skipped so that an expired access cookie never
 * blocks obtaining a new one.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    public static final String ACCESS_COOKIE = "civix_at";
    public static final String COOKIE_AUTHENTICATION_ALLOWED =
            CookieBearerTokenResolver.class.getName() + ".cookieAuthenticationAllowed";

    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9\\-._~+/]+=*$");
    private static final String AUTH_PREFIX = "/api/v1/auth/";
    private static final String ME = "/api/v1/auth/me";

    @Override
    public String resolve(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (path.startsWith(AUTH_PREFIX) && !path.equals(ME)) {
            return null;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = header.substring(7).trim();
            if (token.isEmpty() || !TOKEN.matcher(token).matches()) {
                throw new InvalidBearerTokenException("Bearer token is malformed");
            }
            return token;
        }
        if (!Boolean.TRUE.equals(request.getAttribute(COOKIE_AUTHENTICATION_ALLOWED))) {
            return null;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (ACCESS_COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    String token = cookie.getValue().trim();
                    return TOKEN.matcher(token).matches() ? token : null;
                }
            }
        }
        return null;
    }
}
