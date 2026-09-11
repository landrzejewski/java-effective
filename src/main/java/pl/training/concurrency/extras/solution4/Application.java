package pl.training.concurrency.extras.solution4;

import java.util.Locale;
import pl.training.concurrency.extras.common.ThreadUtils;

import java.time.Duration;
import java.util.ArrayList;

public class Application {

    private static final int CAPACITY = 5;
    private static final Duration REFILL_INTERVAL = Duration.ofMillis(300);
    private static final int REQUESTS = 12;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("[solution4] token bucket: capacity " + CAPACITY
                + ", one new token every " + REFILL_INTERVAL.toMillis() + " ms");

        var tokenBucket = new TokenBucket(CAPACITY, REFILL_INTERVAL.toMillis());
        long start = System.nanoTime();

        var threads = new ArrayList<Thread>();
        for (int index = 0; index < REQUESTS; index++) {
            threads.add(ThreadUtils.asyncRun("request-" + index, () -> {
                tokenBucket.getToken();
                System.out.printf(Locale.ROOT, "  %-12s got a token at %,5d ms%n",
                        Thread.currentThread().getName(), (System.nanoTime() - start) / 1_000_000);
            }));
        }

        ThreadUtils.startAndJoin(threads);

        long elapsed = (System.nanoTime() - start) / 1_000_000;
        long expected = (REQUESTS - CAPACITY) * REFILL_INTERVAL.toMillis();
        System.out.printf(Locale.ROOT, "  all %d requests served in %,d ms (≈ %,d ms expected: %d burst + %d throttled)%n",
                REQUESTS, elapsed, expected, CAPACITY, REQUESTS - CAPACITY);
    }
}
