package io.github.tyllerdaniel.ratelimit;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * One independent limiter per key, for example per API client or per IP address.
 *
 * <p>Limiters are created lazily and atomically with
 * {@link ConcurrentHashMap#computeIfAbsent}, so two threads seeing a new key at
 * the same moment share one limiter instead of each getting a fresh, full one.
 * Idle keys can be dropped with {@link #evictIdle(Duration)} to bound memory.
 */
public final class KeyedRateLimiter<K> {

    private static final class Entry {
        final RateLimiter limiter;
        volatile long lastUsedNanos;

        Entry(RateLimiter limiter, long now) {
            this.limiter = limiter;
            this.lastUsedNanos = now;
        }
    }

    private final ConcurrentHashMap<K, Entry> limiters = new ConcurrentHashMap<>();
    private final Supplier<RateLimiter> factory;
    private final TimeSource time;

    public KeyedRateLimiter(Supplier<RateLimiter> factory) {
        this(factory, TimeSource.SYSTEM);
    }

    public KeyedRateLimiter(Supplier<RateLimiter> factory, TimeSource time) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.time = Objects.requireNonNull(time, "time");
    }

    public boolean tryAcquire(K key) {
        return tryAcquire(key, 1);
    }

    public boolean tryAcquire(K key, int permits) {
        Objects.requireNonNull(key, "key");
        long now = time.nanoTime();
        Entry entry = limiters.computeIfAbsent(key, k -> new Entry(factory.get(), now));
        entry.lastUsedNanos = now;
        return entry.limiter.tryAcquire(permits);
    }

    /**
     * Remove limiters not used for at least {@code idle}. A removed key starts again
     * with a fresh limiter, so choose {@code idle} no shorter than the limiter's own
     * refill period or window.
     *
     * @return the number of keys removed
     */
    public int evictIdle(Duration idle) {
        long cutoff = time.nanoTime() - idle.toNanos();
        int before = limiters.size();
        limiters.entrySet().removeIf(e -> e.getValue().lastUsedNanos - cutoff <= 0);
        return before - limiters.size();
    }

    public int size() {
        return limiters.size();
    }
}
