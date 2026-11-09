package io.github.tyllerdaniel.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenBucketLimiterTest {

    private ManualTimeSource time;

    @BeforeEach
    void setUp() {
        time = new ManualTimeSource();
    }

    @Test
    void startsFullAndAllowsABurstUpToCapacity() {
        TokenBucketLimiter limiter = new TokenBucketLimiter(5, 1.0, time);
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire(), "permit " + i);
        }
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void refillsAtTheConfiguredRate() {
        TokenBucketLimiter limiter = new TokenBucketLimiter(10, 2.0, time); // 2 per second
        assertTrue(limiter.tryAcquire(10));
        assertFalse(limiter.tryAcquire());

        time.advance(Duration.ofMillis(499));
        assertFalse(limiter.tryAcquire(), "0.998 tokens is not a whole permit");

        time.advance(Duration.ofMillis(1));
        assertTrue(limiter.tryAcquire(), "1 token after 500 ms");
        assertFalse(limiter.tryAcquire());

        time.advance(Duration.ofSeconds(2));
        assertEquals(4, limiter.availablePermits());
    }

    @Test
    void neverRefillsBeyondCapacity() {
        TokenBucketLimiter limiter = new TokenBucketLimiter(3, 100.0, time);
        time.advance(Duration.ofHours(1));
        assertEquals(3, limiter.availablePermits());
        assertTrue(limiter.tryAcquire(3));
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void multiPermitRequestsAreAllOrNothing() {
        TokenBucketLimiter limiter = new TokenBucketLimiter(5, 1.0, time);
        assertTrue(limiter.tryAcquire(3));
        assertFalse(limiter.tryAcquire(3), "only 2 left");
        assertEquals(2, limiter.availablePermits(), "a rejected request must not consume tokens");
        assertTrue(limiter.tryAcquire(2));
    }

    @Test
    void clockGoingBackwardsDoesNotCreateTokens() {
        TokenBucketLimiter limiter = new TokenBucketLimiter(2, 1.0, time);
        assertTrue(limiter.tryAcquire(2));
        time.advance(Duration.ofSeconds(-10));
        assertFalse(limiter.tryAcquire());
        time.advance(Duration.ofSeconds(10));
        assertFalse(limiter.tryAcquire(), "returning to the original time must not refill the same interval twice");
        time.advance(Duration.ofSeconds(1));
        assertTrue(limiter.tryAcquire());
    }

    @Test
    void validatesArguments() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketLimiter(0, 1.0, time));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketLimiter(1, 0.0, time));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketLimiter(1, Double.NaN, time));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketLimiter(1, Double.POSITIVE_INFINITY, time));

        TokenBucketLimiter limiter = new TokenBucketLimiter(5, 1.0, time);
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(6), "could never succeed");
    }
}
