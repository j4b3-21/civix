package com.civix.auth.service;

import java.time.Instant;

import com.civix.auth.dto.UserDto;

/** Outcome of register/login/refresh: the user plus freshly issued tokens (written to cookies by the controller). */
public record AuthResult(UserDto user, String accessToken, Instant accessExpiresAt, String refreshToken,
                         Instant refreshExpiresAt) {

    @Override
    public String toString() {
        return "AuthResult[user=" + user.id() + "]";
    }
}
