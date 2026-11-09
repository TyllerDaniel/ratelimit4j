package io.github.tyllerdaniel.ratelimit;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/** A clock that only moves when a test says so. Starts at an arbitrary negative value on purpose. */
final class ManualTimeSource implements TimeSource {

    private final AtomicLong now = new AtomicLong(-5_000_000_000L);

    @Override
    public long nanoTime() {
        return now.get();
    }

    void advance(Duration d) {
        now.addAndGet(d.toNanos());
    }

    void set(long nanos) {
        now.set(nanos);
    }
}
