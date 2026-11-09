package io.github.tyllerdaniel.ratelimit;

import java.time.Duration;

/** Static factories for the common cases. */
public final class RateLimiters {

    private RateLimiters() {}

    /** Bursts up to {@code burst}, sustained {@code perSecond} on average. */
    public static RateLimiter tokenBucket(long burst, double perSecond) {
        return new TokenBucketLimiter(burst, perSecond);
    }

    /** At most {@code limit} permits in any rolling {@code window}. */
    public static RateLimiter slidingWindow(int limit, Duration window) {
        return new SlidingWindowLimiter(limit, window);
    }

    /** A per-key token bucket, for example one per API client. */
    public static <K> KeyedRateLimiter<K> perKeyTokenBucket(long burst, double perSecond) {
        return new KeyedRateLimiter<>(() -> new TokenBucketLimiter(burst, perSecond));
    }
}
