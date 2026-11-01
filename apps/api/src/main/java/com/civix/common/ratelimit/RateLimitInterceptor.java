package com.civix.common.ratelimit;

import java.time.Duration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.HandlerInterceptor;

import com.civix.common.security.CurrentUser;
import com.civix.common.web.ClientIp;

/**
 * Applies one rate limit to the request paths it is registered for (see {@code WebConfig}).
 * Only the configured HTTP method is limited.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    public enum Scope { IP, USER }

    private final RateLimiter limiter;
    private final String bucket;
    private final String method;
    private final Scope scope;
    private final int limit;
    private final Duration window;

    public RateLimitInterceptor(RateLimiter limiter, String bucket, String method, Scope scope, int limit, Duration window) {
        this.limiter = limiter;
        this.bucket = bucket;
        this.method = method;
        this.scope = scope;
        this.limit = limit;
        this.window = window;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!method.equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String subject = switch (scope) {
            case IP -> "ip:" + ClientIp.of(request);
            case USER -> CurrentUser.idIfAuthenticated().map(id -> "user:" + id).orElse("ip:" + ClientIp.of(request));
        };
        limiter.check(bucket, subject, limit, window);
        return true;
    }
}
