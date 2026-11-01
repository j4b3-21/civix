package com.civix.config;

import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.civix.common.ratelimit.RateLimitInterceptor;
import com.civix.common.ratelimit.RateLimitInterceptor.Scope;
import com.civix.common.ratelimit.RateLimiter;

/** Rate limits from docs/API.md: auth 10/min per IP, discovery 20/min per user, uploads 30/hour per user. */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    private final RateLimiter rateLimiter;

    public WebConfig(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(rateLimiter, "auth", "POST", Scope.IP, 10, Duration.ofMinutes(1)))
                .addPathPatterns("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/refresh");
        registry.addInterceptor(new RateLimitInterceptor(rateLimiter, "discover", "POST", Scope.USER, 20, Duration.ofMinutes(1)))
                .addPathPatterns("/api/v1/services/discover");
        registry.addInterceptor(new RateLimitInterceptor(rateLimiter, "upload", "POST", Scope.USER, 30, Duration.ofHours(1)))
                .addPathPatterns("/api/v1/documents");
    }
}
