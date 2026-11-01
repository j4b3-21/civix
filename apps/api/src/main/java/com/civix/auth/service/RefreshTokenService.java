package com.civix.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.civix.auth.domain.RefreshToken;
import com.civix.auth.repository.RefreshTokenRepository;
import com.civix.common.api.StateChangingApiException;
import com.civix.common.crypto.Hashing;
import com.civix.config.CivixProperties;

/**
 * Opaque refresh tokens: 32 random bytes (base64url), stored SHA-256-hashed, rotated on every use.
 * Presenting a token that was already rotated is treated as theft: the whole family is revoked.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository tokens;
    private final Duration ttl;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository tokens, CivixProperties props, Clock clock) {
        this.tokens = tokens;
        this.ttl = Duration.ofDays(props.jwt().refreshTtlDays());
        this.clock = clock;
    }

    /** Starts a new family (login / registration). */
    @Transactional
    public IssuedRefreshToken issueNewFamily(UUID userId, String userAgent) {
        return issue(userId, UUID.randomUUID(), userAgent);
    }

    /**
     * Rotates {@code rawToken}. On reuse of an already-rotated token the family is revoked (and that revocation
     * is committed) before {@code 401 REFRESH_TOKEN_REUSED} is raised.
     */
    @Transactional(noRollbackFor = StateChangingApiException.class)
    public Rotation rotate(String rawToken, String userAgent) {
        Instant now = clock.instant();
        RefreshToken current = tokens.findByTokenHashForUpdate(Hashing.sha256Hex(rawToken))
                .orElseThrow(RefreshTokenService::invalid);
        if (current.isRevoked()) {
            if (current.getReplacedBy() != null) {
                int revoked = tokens.revokeFamily(current.getFamilyId(), now);
                log.warn("Refresh token reuse detected; revoked {} token(s) of family {}", revoked, current.getFamilyId());
                throw new StateChangingApiException("REFRESH_TOKEN_REUSED", HttpStatus.UNAUTHORIZED,
                        "This session was revoked for your protection. Please sign in again.");
            }
            throw invalid();
        }
        if (current.isExpired(now)) {
            current.revoke(now, null);
            throw new StateChangingApiException("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED,
                    "Your session has expired. Please sign in again.");
        }
        IssuedRefreshToken next = issue(current.getUserId(), current.getFamilyId(), userAgent);
        current.revoke(now, next.id());
        tokens.save(current);
        return new Rotation(current.getUserId(), current.getFamilyId(), next);
    }

    /** Revokes the family of the presented token (logout). Returns the owning user, if the token was known. */
    @Transactional
    public Optional<UUID> revokeFamilyOf(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(Hashing.sha256Hex(rawToken)).map(t -> {
            tokens.revokeFamily(t.getFamilyId(), clock.instant());
            return t.getUserId();
        });
    }

    @Transactional
    public void revokeAll(UUID userId) {
        tokens.revokeAllForUser(userId, clock.instant());
    }

    public Duration ttl() {
        return ttl;
    }

    /** Housekeeping: expired tokens older than a day are no longer needed for reuse detection. */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 300_000L)
    @Transactional
    public void purgeExpired() {
        int removed = tokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
        if (removed > 0) {
            log.info("Purged {} expired refresh token(s)", removed);
        }
    }

    private IssuedRefreshToken issue(UUID userId, UUID familyId, String userAgent) {
        Instant now = clock.instant();
        String raw = Hashing.randomToken(TOKEN_BYTES);
        String ua = userAgent == null ? null : userAgent.substring(0, Math.min(255, userAgent.length()));
        RefreshToken saved = tokens.save(new RefreshToken(userId, Hashing.sha256Hex(raw), familyId, now.plus(ttl), ua, now));
        return new IssuedRefreshToken(saved.getId(), raw, saved.getExpiresAt());
    }

    private static StateChangingApiException invalid() {
        return new StateChangingApiException("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED,
                "Your session is no longer valid. Please sign in again.");
    }

    public record IssuedRefreshToken(UUID id, String rawToken, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedRefreshToken[id=" + id + ", expiresAt=" + expiresAt + "]";
        }
    }

    public record Rotation(UUID userId, UUID familyId, IssuedRefreshToken token) {
    }
}
