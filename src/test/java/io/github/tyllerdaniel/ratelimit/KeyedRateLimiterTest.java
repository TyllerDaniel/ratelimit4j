package io.github.tyllerdaniel.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class KeyedRateLimiterTest {

    private final ManualTimeSource time = new ManualTimeSource();

    private KeyedRateLimiter<String> twoPerKey() {
        return new KeyedRateLimiter<>(() -> new TokenBucketLimiter(2, 1.0, time), time);
    }

    @Test
    void keysAreIndependent() {
        KeyedRateLimiter<String> limiter = twoPerKey();
        assertTrue(limiter.tryAcquire("alice"));
        assertTrue(limiter.tryAcquire("alice"));
        assertFalse(limiter.tryAcquire("alice"));
        assertTrue(limiter.tryAcquire("bob"), "bob has his own bucket");
        assertEquals(2, limiter.size());
    }

    @Test
    void evictsOnlyIdleKeys() {
        KeyedRateLimiter<String> limiter = twoPerKey();
        limiter.tryAcquire("old");
        time.advance(Duration.ofMinutes(10));
        limiter.tryAcquire("recent");

        assertEquals(1, limiter.evictIdle(Duration.ofMinutes(5)));
        assertEquals(1, limiter.size());
        assertEquals(0, limiter.evictIdle(Duration.ofMinutes(5)));
    }

    @Test
    void concurrentFirstRequestsShareOneLimiter() throws Exception {
        // If two threads could each create a fresh limiter for a new key, a client
        // could get double its quota on its very first burst.
        AtomicInteger created = new AtomicInteger();
        KeyedRateLimiter<String> limiter = new KeyedRateLimiter<>(() -> {
            created.incrementAndGet();
            return new TokenBucketLimiter(10, 1.0, time);
        }, time);

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger granted = new AtomicInteger();
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < 5; i++) {
                    if (limiter.tryAcquire("new-client")) {
                        granted.incrementAndGet();
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(1, created.get());
        assertEquals(10, granted.get());
    }

    @Test
    void rejectsNullKey() {
        assertThrows(NullPointerException.class, () -> twoPerKey().tryAcquire(null));
    }
}
