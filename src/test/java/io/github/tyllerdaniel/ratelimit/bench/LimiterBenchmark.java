package io.github.tyllerdaniel.ratelimit.bench;

import io.github.tyllerdaniel.ratelimit.SlidingWindowLimiter;
import io.github.tyllerdaniel.ratelimit.TokenBucketLimiter;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

/**
 * Cost of a tryAcquire() call, alone and under contention.
 *
 * <p>Limits are set so requests are almost always granted, which exercises the
 * expensive path (the CAS in the token bucket, the ring-buffer write in the
 * sliding window). Run with: {@code mvn -Pbench test-compile exec:exec}
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class LimiterBenchmark {

    private TokenBucketLimiter tokenBucket;
    private SlidingWindowLimiter slidingWindow;

    @Setup
    public void setUp() {
        tokenBucket = new TokenBucketLimiter(1L << 40, 1e15);
        // A 1 ns window means every earlier admission has already expired.
        slidingWindow = new SlidingWindowLimiter(1024, Duration.ofNanos(1));
    }

    @Benchmark
    @Threads(1)
    public boolean tokenBucketSingleThread() {
        return tokenBucket.tryAcquire();
    }

    @Benchmark
    @Threads(8)
    public boolean tokenBucketEightThreads() {
        return tokenBucket.tryAcquire();
    }

    @Benchmark
    @Threads(1)
    public boolean slidingWindowSingleThread() {
        return slidingWindow.tryAcquire();
    }

    @Benchmark
    @Threads(8)
    public boolean slidingWindowEightThreads() {
        return slidingWindow.tryAcquire();
    }
}
