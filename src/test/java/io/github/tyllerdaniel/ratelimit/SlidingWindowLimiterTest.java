package io.github.tyllerdaniel.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlidingWindowLimiterTest {

    private ManualTimeSource time;

    @BeforeEach
    void setUp() {
        time = new ManualTimeSource();
    }

    @Test
    void allowsLimitPerWindow() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(3, Duration.ofSeconds(1), time);
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void permitsExpireExactlyOneWindowAfterTheyWereGranted() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(2, Duration.ofSeconds(10), time);
        assertTrue(limiter.tryAcquire()); // t = 0
        time.advance(Duration.ofSeconds(4));
        assertTrue(limiter.tryAcquire()); // t = 4
        assertFalse(limiter.tryAcquire());

        time.advance(Duration.ofSeconds(6).minusNanos(1)); // t = 10 - 1ns
        assertFalse(limiter.tryAcquire(), "first permit is still inside the window");
        time.advance(Duration.ofNanos(1)); // t = 10
        assertTrue(limiter.tryAcquire(), "first permit has expired");
        assertFalse(limiter.tryAcquire(), "second permit (t=4) is still inside the window");
    }

    @Test
    void noBurstAcrossABoundaryUnlikeAFixedWindow() {
        // A fixed one-second window would allow 5 at 0.99s and 5 more at 1.01s.
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(5, Duration.ofSeconds(1), time);
        time.advance(Duration.ofMillis(990));
        assertTrue(limiter.tryAcquire(5));
        time.advance(Duration.ofMillis(20));
        assertFalse(limiter.tryAcquire());
        assertEquals(0, limiter.availablePermits());
    }

    @Test
    void ringBufferWrapsAroundCorrectly() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(3, Duration.ofMillis(100), time);
        for (int round = 0; round < 50; round++) {
            assertTrue(limiter.tryAcquire(3), "round " + round);
            assertFalse(limiter.tryAcquire());
            time.advance(Duration.ofMillis(100));
        }
    }

    @Test
    void multiPermitRequestsAreAllOrNothing() {
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(4, Duration.ofSeconds(1), time);
        assertTrue(limiter.tryAcquire(3));
        assertFalse(limiter.tryAcquire(2));
        assertEquals(1, limiter.availablePermits());
    }

    @Test
    void validatesArguments() {
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowLimiter(0, Duration.ofSeconds(1), time));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowLimiter(1, Duration.ZERO, time));
        assertThrows(IllegalArgumentException.class, () -> new SlidingWindowLimiter(1, Duration.ofSeconds(-1), time));
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(2, Duration.ofSeconds(1), time);
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(3));
    }
}
