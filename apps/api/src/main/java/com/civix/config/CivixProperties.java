package com.civix.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the {@code civix.*} configuration tree (populated from the environment, see {@code .env.example}).
 */
@ConfigurationProperties(prefix = "civix")
public record CivixProperties(
        String env,
        String publicUrl,
        boolean enableTestServices,
        String adminEmails,
        Jwt jwt,
        Cookie cookie,
        Audit audit,
        Internal internal,
        Approval approval,
        Agent agent,
        Storage storage,
        Registry registry,
        RateLimit rateLimit,
        Sse sse) {

    public record Jwt(String secret, int accessTtlMinutes, int refreshTtlDays) {
    }

    public record Cookie(boolean secure) {
    }

    public record Audit(String ipPepper) {
    }

    public record Internal(String apiToken) {
    }

    public record Approval(String signingSecret) {
    }

    public record Agent(String baseUrl, String token, int connectTimeoutMs, int readTimeoutMs, int longReadTimeoutMs) {
    }

    public record Storage(String endpoint, String region, String bucket, String accessKey, String secretKey,
                          boolean forcePathStyle, String sse, boolean initBucket) {
    }

    public record Registry(boolean syncOnStartup, int retryIntervalSeconds) {
    }

    public record RateLimit(boolean enabled) {
    }

    public record Sse(boolean pollFallback) {
    }

    /** Lower-cased set of e-mail addresses that are promoted to ADMIN on registration. */
    public Set<String> adminEmailSet() {
        if (adminEmails == null || adminEmails.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(adminEmails.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isProduction() {
        return "production".equalsIgnoreCase(env);
    }
}
