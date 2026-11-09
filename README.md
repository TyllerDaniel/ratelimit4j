# ratelimit4j

[![CI](https://github.com/TyllerDaniel/ratelimit4j/actions/workflows/ci.yml/badge.svg)](https://github.com/TyllerDaniel/ratelimit4j/actions/workflows/ci.yml)

Small, dependency-free, thread-safe rate limiters for Java 17+.

| Limiter | Behaviour | Concurrency |
| --- | --- | --- |
| `TokenBucketLimiter` | Bursts up to a capacity, refills continuously at a fixed rate | Lock-free (single CAS on immutable state) |
| `SlidingWindowLimiter` | At most N permits in any rolling window, measured exactly | `synchronized`, O(1) amortised |
| `KeyedRateLimiter<K>` | One independent limiter per key (client, user, IP) with idle eviction | `ConcurrentHashMap.computeIfAbsent` |

```java
RateLimiter api = RateLimiters.tokenBucket(20, 5.0);        // bursts of 20, 5/s sustained
if (!api.tryAcquire()) {
    return Response.status(429).build();
}

KeyedRateLimiter<String> perClient = RateLimiters.perKeyTokenBucket(10, 1.0);
if (!perClient.tryAcquire(apiKey)) { ... }

RateLimiter logins = RateLimiters.slidingWindow(5, Duration.ofMinutes(1));
```

All limiters are non-blocking: `tryAcquire` answers immediately, and
multi-permit requests are all-or-nothing.

## Correctness first

The interesting part of a rate limiter is not the arithmetic but what happens
when 16 threads ask for the last token at the same moment.
[docs/race-condition.md](docs/race-condition.md) explains the two races a token
bucket has to avoid and how this one avoids them:

1. **Check-then-act over atomic fields** grants more than the capacity under
   contention. Fixed by keeping tokens and the refill time in one immutable
   state swapped with a single `compareAndSet`.
2. **Out-of-order clock reads across threads** can move the bucket's clock
   backwards and refill the same interval twice. Fixed by never letting the
   timestamp go backwards.

### How it is tested

* **Deterministic time.** Every limiter takes a `TimeSource`, so unit tests
  drive a manual clock instead of sleeping. They cover partial refills, the
  exact expiry instant of the sliding window, ring-buffer wrap-around, a clock
  going backwards, and the double-burst that a fixed-window counter would allow
  at a boundary.
* **Exact counts under contention.** With the clock frozen, 16 threads
  released together through a latch make 80,000 attempts against a budget of
  1,000. A correct limiter grants exactly 1,000, every time, for 20 trials.
* **The stress test is itself tested.** It must catch a deliberately naive
  check-then-act limiter (`NaiveTokenBucket`, test scope only), and it does,
  including with the JVM pinned to one CPU. A concurrency test that cannot fail
  is not evidence of anything.
* **Real-clock bound.** Under contention with the system clock, admissions
  stay within `burst + rate × elapsed`.

## Benchmarks

JMH benchmarks measure `tryAcquire` on the grant path, single-threaded and with
8 threads contending:

```bash
mvn -Pbench test-compile exec:exec
mvn -Pbench test-compile exec:exec -Djmh.args="-f 3 -wi 5 -i 10"   # longer, steadier run
```

## Build

```bash
mvn verify
```

Requires JDK 17 or newer. CI runs on 17 and 21 and also runs a short benchmark
smoke test.

## Choosing a limiter

* **Token bucket** when short bursts are fine but the average rate matters, as
  for most API clients.
* **Sliding window** when the limit is a hard rule over any window, such as
  "5 login attempts per minute". It uses memory proportional to the limit.
* **Keyed** when each client needs its own budget. Call `evictIdle` periodically
  so memory does not grow with every client ever seen.

## License

MIT
