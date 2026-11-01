package com.civix.common.ratelimit;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.civix.common.api.RateLimitExceededException;
import com.civix.common.crypto.Hashing;
import com.civix.config.CivixProperties;

/**
 * Fixed-window rate limiter on Redis ({@code INCR} + {@code EXPIRE}). Fails open when Redis is unavailable
 * (logged at most once a minute) so that an outage of the cache never locks citizens out.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private static final long LOG_INTERVAL_MS = 60_000;

    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final AtomicLong lastFailureLog = new AtomicLong();

    public RateLimiter(StringRedisTemplate redis, CivixProperties props) {
        this.redis = redis;
        this.enabled = props.rateLimit() == null || props.rateLimit().enabled();
    }

    /**
     * @param bucket  logical limit name, e.g. {@code auth}
     * @param subject client identity (IP or user id); hashed before it becomes part of a Redis key
     * @throws RateLimitExceededException when the limit is exceeded in the current window
     */
    public void check(String bucket, String subject, int limit, Duration window) {
        if (!enabled) {
            return;
        }
        String key = "civix:rl:" + bucket + ":" + Hashing.sha256Hex(subject).substring(0, 32);
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) {
                return;
            }
            if (count == 1L) {
                redis.expire(key, window);
            }
            if (count > limit) {
                Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
                if (ttl == null || ttl < 0) {
                    redis.expire(key, window);
                    ttl = window.toSeconds();
                }
                throw new RateLimitExceededException(Math.max(1L, ttl));
            }
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (RuntimeException e) {
            long now = System.currentTimeMillis();
            long last = lastFailureLog.get();
            if (now - last > LOG_INTERVAL_MS && lastFailureLog.compareAndSet(last, now)) {
                log.warn("Rate limiter unavailable (failing open): {}", e.getClass().getSimpleName());
            }
        }
    }
}
