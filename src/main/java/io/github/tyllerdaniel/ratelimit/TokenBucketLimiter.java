package io.github.tyllerdaniel.ratelimit;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Token bucket: holds up to {@code capacity} tokens and refills continuously at
 * {@code refillPerSecond}. Allows bursts up to the capacity while enforcing the
 * long-run average rate.
 *
 * <p><b>Concurrency.</b> The whole state (tokens and the time they were last
 * refilled) lives in one immutable object swapped with compare-and-set. Refill
 * and consume therefore happen as a single atomic step, which is what prevents
 * over-admission under contention. See {@code docs/race-condition.md} for the
 * bug this design avoids.
 */
public final class TokenBucketLimiter implements RateLimiter {

    private record State(double tokens, long timestampNanos) {}

    private final long capacity;
    private final double tokensPerNano;
    private final TimeSource time;
    private final AtomicReference<State> state;

    public TokenBucketLimiter(long capacity, double refillPerSecond) {
        this(capacity, refillPerSecond, TimeSource.SYSTEM);
    }

    public TokenBucketLimiter(long capacity, double refillPerSecond, TimeSource time) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, got " + capacity);
        }
        if (!(refillPerSecond > 0) || Double.isInfinite(refillPerSecond)) {
            throw new IllegalArgumentException("refillPerSecond must be positive and finite, got " + refillPerSecond);
        }
        this.capacity = capacity;
        this.tokensPerNano = refillPerSecond / 1_000_000_000.0;
        this.time = time;
        this.state = new AtomicReference<>(new State(capacity, time.nanoTime()));
    }

    @Override
    public boolean tryAcquire(int permits) {
        if (permits <= 0 || permits > capacity) {
            throw new IllegalArgumentException("permits must be in [1, " + capacity + "], got " + permits);
        }
        while (true) {
            State current = state.get();
            State refilled = refill(current, time.nanoTime());
            if (refilled.tokens() < permits) {
                return false;
            }
            State next = new State(refilled.tokens() - permits, refilled.timestampNanos());
            if (state.compareAndSet(current, next)) {
                return true;
            }
            // Another thread changed the state between our read and our write. Retry
            // from its result rather than overwrite it.
        }
    }

    @Override
    public long availablePermits() {
        return (long) Math.floor(refill(state.get(), time.nanoTime()).tokens());
    }

    private State refill(State s, long now) {
        // nanoTime() readings can arrive out of order across threads: a thread that
        // read the clock earlier may run its CAS after one that read it later.
        // Never move the timestamp backwards, or the same interval is refilled twice.
        if (now <= s.timestampNanos()) {
            return s;
        }
        double tokens = Math.min(capacity, s.tokens() + (now - s.timestampNanos()) * tokensPerNano);
        return new State(tokens, now);
    }

    public long capacity() {
        return capacity;
    }

    @Override
    public String toString() {
        return "TokenBucketLimiter[capacity=" + capacity + ", refillPerSecond=" + tokensPerNano * 1e9 + "]";
    }
}
