package io.github.tyllerdaniel.ratelimit;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A deliberately broken limiter, kept only to prove the stress test can catch
 * the bug described in docs/race-condition.md.
 *
 * <p>Every field is atomic, yet the class is not thread-safe: "check there are
 * enough tokens" and "take them" are two separate steps, so several threads can
 * pass the check on the same last token before any of them subtracts it.
 */
final class NaiveTokenBucket implements RateLimiter {

    private final AtomicLong tokens;

    NaiveTokenBucket(long capacity) {
        this.tokens = new AtomicLong(capacity);
    }

    @Override
    public boolean tryAcquire(int permits) {
        if (tokens.get() >= permits) { // check ...
            Thread.yield(); // widens the race window so the test can observe it reliably
            tokens.addAndGet(-permits); // ... then act: not atomic with the check
            return true;
        }
        return false;
    }

    @Override
    public long availablePermits() {
        return Math.max(0, tokens.get());
    }
}
