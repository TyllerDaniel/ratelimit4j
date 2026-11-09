package io.github.tyllerdaniel.ratelimit;

/**
 * Monotonic time in nanoseconds. Injected so tests can control time instead of sleeping.
 */
@FunctionalInterface
public interface TimeSource {

    /** The system monotonic clock ({@link System#nanoTime()}). */
    TimeSource SYSTEM = System::nanoTime;

    long nanoTime();
}
