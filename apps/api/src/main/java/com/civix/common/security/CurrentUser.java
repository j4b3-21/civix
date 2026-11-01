package com.civix.common.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.civix.common.api.ApiException;

/** Resolves the authenticated citizen (JWT {@code sub}) from the security context. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id() {
        return idIfAuthenticated().orElseThrow(
                () -> new ApiException("UNAUTHENTICATED", HttpStatus.UNAUTHORIZED, "Authentication is required"));
    }

    public static Optional<UUID> idIfAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt && jwt.isAuthenticated()) {
            try {
                return Optional.of(UUID.fromString(jwt.getToken().getSubject()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
