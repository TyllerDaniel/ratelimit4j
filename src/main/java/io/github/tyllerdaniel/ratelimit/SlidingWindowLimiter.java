package io.github.tyllerdaniel.ratelimit;

import java.time.Duration;

/**
 * Sliding window log: at most {@code limit} permits in any window of length
 * {@code window}, measured exactly rather than in fixed buckets. A fixed-window
 * counter would allow up to twice the limit across a bucket boundary; this does not.
 *
 * <p><b>Design.</b> The timestamps of admitted permits are kept in a ring buffer
 * of size {@code limit}, so memory is O(limit) and both admitting and expiring
 * are O(1) amortised. Operations are short and touch several fields together,
 * so a plain lock is simpler and safer here than lock-free code.
 */
public final class SlidingWindowLimiter implements RateLimiter {

    private final int limit;
    private final long windowNanos;
    private final TimeSource time;

    private final long[] admitted; // ring buffer of admission timestamps, oldest at head
    private int head;
    private int size;

    public SlidingWindowLimiter(int limit, Duration window) {
        this(limit, window, TimeSource.SYSTEM);
    }

    public SlidingWindowLimiter(int limit, Duration window, TimeSource time) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive, got " + limit);
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive, got " + window);
        }
        this.limit = limit;
        this.windowNanos = window.toNanos();
        this.time = time;
        this.admitted = new long[limit];
    }

    @Override
    public synchronized boolean tryAcquire(int permits) {
        if (permits <= 0 || permits > limit) {
            throw new IllegalArgumentException("permits must be in [1, " + limit + "], got " + permits);
        }
        long now = time.nanoTime();
        expire(now);
        if (limit - size < permits) {
            return false;
        }
        for (int i = 0; i < permits; i++) {
            admitted[(head + size) % limit] = now;
            size++;
        }
        return true;
    }

    @Override
    public synchronized long availablePermits() {
        expire(time.nanoTime());
        return limit - size;
    }

    /** Drop admissions that are a full window old or older. */
    private void expire(long now) {
        // Compare differences, not absolute values: nanoTime() may be negative or wrap.
        while (size > 0 && now - admitted[head] >= windowNanos) {
            head = (head + 1) % limit;
            size--;
        }
    }

    @Override
    public String toString() {
        return "SlidingWindowLimiter[limit=" + limit + ", window=" + Duration.ofNanos(windowNanos) + "]";
    }
}
