# Two race conditions a token bucket has to avoid

Both bugs below pass every single-threaded unit test. They only show up under
contention, which is why this project has a dedicated stress test and checks
that the stress test can actually catch them.

## 1. Check-then-act on atomic fields

The obvious implementation keeps the token count in an `AtomicLong`:

```java
if (tokens.get() >= permits) {     // check
    tokens.addAndGet(-permits);    // act
    return true;
}
return false;
```

Each line is atomic, but the pair is not. With one token left, several threads
can all read `tokens == 1`, all pass the check, and all subtract. The limiter
grants more than its capacity and the count goes negative. Making each field
atomic does not make the class thread-safe; the invariant ("never grant more
than you have") spans the check and the update.

The same mistake appears with refill: storing `tokens` and `lastRefill` in two
separate atomics means one thread can refill using another thread's stale
timestamp, refilling the same interval twice.

**Fix.** `TokenBucketLimiter` keeps tokens and the refill timestamp together in
one immutable `State` record, held in an `AtomicReference`. Each attempt reads
the state, computes the refilled and consumed state, and publishes it with a
single `compareAndSet`. If another thread won the race, the CAS fails and the
loop retries from the new state. Refill and consume become one atomic step,
without a lock.

**How the test proves it.** `ConcurrencyStressTest` freezes the clock (so
nothing refills), releases 16 threads at the same instant through a latch, and
has each attempt 5,000 acquisitions against a bucket of 1,000. A correct limiter
must grant exactly 1,000 every time: one more is over-admission, one fewer is a
lost permit. It repeats this 20 times per limiter.

A stress test that never fails proves nothing, so
`stressTestCatchesTheNaiveCheckThenActBug` runs the same harness against
`NaiveTokenBucket` (the code above, test scope only) and requires it to
over-admit. It does, typically on the first trial, including when the JVM is
pinned to a single CPU.

## 2. Clock readings arriving out of order

`System.nanoTime()` is monotonic for one thread, but across threads the reads
and the CASes can interleave:

1. Thread A reads `now = 100`.
2. Thread B reads `now = 105`, refills up to 105 and wins its CAS.
3. Thread A computes `elapsed = 100 - 105 = -5`.

If A then stores `timestamp = 100`, the bucket's clock has gone backwards, and
the next caller refills the interval 100 to 105 a second time. Negative elapsed
time can also subtract tokens.

**Fix.** `refill()` never moves the timestamp backwards: if `now` is not after
the stored timestamp, the state is returned unchanged.
`clockGoingBackwardsDoesNotCreateTokens` drives this case deterministically
with a manual clock, which is how time is controlled in all the unit tests
instead of sleeping.

## Why the sliding window uses a lock

`SlidingWindowLimiter` has to expire old entries from a ring buffer and append
new ones, touching the head index, the size and the array together. A
lock-free version would need far more subtle code for little gain at these
operation sizes, so it uses `synchronized`. The same stress test verifies it
grants exactly its limit under contention.
