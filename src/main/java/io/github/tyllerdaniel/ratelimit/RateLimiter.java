package io.github.tyllerdaniel.ratelimit;

/**
 * A thread-safe, non-blocking rate limiter.
 *
 * <p>Implementations never block: {@link #tryAcquire(int)} answers immediately,
 * so callers decide whether to reject, queue or retry.
 */
public interface RateLimiter {

    /** Try to take one permit. */
    default boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * Try to take {@code permits} permits atomically: either all are granted or none.
     *
     * @throws IllegalArgumentException if {@code permits} is not positive or exceeds
     *     what the limiter could ever grant at once
     */
    boolean tryAcquire(int permits);

    /** Permits that could be granted right now. A snapshot; it may change immediately. */
    long availablePermits();
}
