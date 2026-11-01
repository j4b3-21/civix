package com.civix.auth.service;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.civix.common.security.CookieBearerTokenResolver;
import com.civix.config.CivixProperties;

/** httpOnly auth cookies: {@code civix_at} (Path /) and {@code civix_rt} (Path /api/v1/auth), SameSite=Lax. */
@Component
public class AuthCookies {

    public static final String ACCESS_COOKIE = CookieBearerTokenResolver.ACCESS_COOKIE;
    public static final String REFRESH_COOKIE = "civix_rt";
    public static final String REFRESH_PATH = "/api/v1/auth";

    private final boolean secure;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public AuthCookies(CivixProperties props) {
        this.secure = props.cookie().secure();
        this.accessTtl = Duration.ofMinutes(props.jwt().accessTtlMinutes());
        this.refreshTtl = Duration.ofDays(props.jwt().refreshTtlDays());
    }

    public HttpHeaders issue(AuthResult result) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, result.accessToken(), "/", accessTtl).toString());
        headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, result.refreshToken(), REFRESH_PATH, refreshTtl).toString());
        return headers;
    }

    public HttpHeaders clear() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, "", "/", Duration.ZERO).toString());
        headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", REFRESH_PATH, Duration.ZERO).toString());
        return headers;
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
