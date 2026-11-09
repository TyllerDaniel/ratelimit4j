package io.github.tyllerdaniel.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Many threads race for a fixed budget of permits. With the clock frozen no
 * tokens are refilled, so a correct limiter must grant exactly its capacity:
 * one more is over-admission, one fewer is a lost permit.
 */
class ConcurrencyStressTest {

    private static final int THREADS = 16;
    private static final int ATTEMPTS_PER_THREAD = 5_000;
    private static final int CAPACITY = 1_000;

    /** Returns how many permits were granted in total across all threads. */
    static int hammer(RateLimiter limiter) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                Callable<Integer> worker = () -> {
                    ready.countDown();
                    start.await(); // release every thread at once to maximise contention
                    int granted = 0;
                    for (int i = 0; i < ATTEMPTS_PER_THREAD; i++) {
                        if (limiter.tryAcquire()) {
                            granted++;
                        }
                    }
                    return granted;
                };
                results.add(pool.submit(worker));
            }
            ready.await();
            start.countDown();
            int total = 0;
            for (Future<Integer> f : results) {
                total += f.get(60, TimeUnit.SECONDS);
            }
            return total;
        } finally {
            pool.shutdownNow();
        }
    }

    private static void assertExactlyCapacity(Supplier<RateLimiter> factory) throws Exception {
        for (int trial = 0; trial < 20; trial++) {
            assertEquals(CAPACITY, hammer(factory.get()), "trial " + trial);
        }
    }

    @Test
    void tokenBucketGrantsExactlyItsCapacityUnderContention() throws Exception {
        ManualTimeSource frozen = new ManualTimeSource();
        assertExactlyCapacity(() -> new TokenBucketLimiter(CAPACITY, 1.0, frozen));
    }

    @Test
    void slidingWindowGrantsExactlyItsLimitUnderContention() throws Exception {
        ManualTimeSource frozen = new ManualTimeSource();
        assertExactlyCapacity(() -> new SlidingWindowLimiter(CAPACITY, Duration.ofSeconds(1), frozen));
    }

    @Test
    void tokenBucketRespectsTheRateWithARealClock() throws Exception {
        // 1,000 burst + 2,000/s. However long the run takes, admissions are bounded
        // by burst + rate * elapsed.
        double perSecond = 2_000;
        long started = System.nanoTime();
        int granted = hammer(new TokenBucketLimiter(CAPACITY, perSecond));
        double elapsedSeconds = (System.nanoTime() - started) / 1e9;
        double bound = CAPACITY + perSecond * elapsedSeconds;
        assertTrue(granted <= bound + 1, "granted " + granted + " but bound is " + bound);
    }

    @Test
    void stressTestCatchesTheNaiveCheckThenActBug() throws Exception {
        // The test is only worth something if it fails on a buggy limiter.
        // Retry a few times because a race is, by nature, not guaranteed on every run.
        int worst = 0;
        for (int trial = 0; trial < 50 && worst <= CAPACITY; trial++) {
            worst = Math.max(worst, hammer(new NaiveTokenBucket(CAPACITY)));
        }
        assertTrue(worst > CAPACITY, "expected the naive limiter to over-admit, max granted was " + worst);
    }
}
