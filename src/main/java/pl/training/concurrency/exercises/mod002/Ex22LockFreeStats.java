package pl.training.concurrency.exercises.mod002;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Exercise 2.2 — Lock-free statistics.
 *
 * <p>Related: Mod002 §1, §4, §5
 */
public final class Ex22LockFreeStats {

    private Ex22LockFreeStats() {}

    interface Stats {
        void record(int value);
        long count();
        long sum();
        int min();
        int max();
    }

    /** min/max maintained with hand-written CAS loops. */
    static final class CasStats implements Stats {
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong sum = new AtomicLong();
        private final AtomicInteger min = new AtomicInteger(Integer.MAX_VALUE);
        private final AtomicInteger max = new AtomicInteger(Integer.MIN_VALUE);
        final AtomicLong casRetries = new AtomicLong();

        @Override public void record(int value) {
            count.incrementAndGet();
            sum.addAndGet(value);
            casMin(value);
            casMax(value);
        }

        private void casMin(int value) {
            while (true) {
                int current = min.get();
                if (value >= current) {
                    return;                                 // nothing to do
                }
                if (min.compareAndSet(current, value)) {
                    return;                                 // we won
                }
                casRetries.incrementAndGet();               // somebody else changed it — re-read and retry
            }
        }

        private void casMax(int value) {
            while (true) {
                int current = max.get();
                if (value <= current) {
                    return;
                }
                if (max.compareAndSet(current, value)) {
                    return;
                }
                casRetries.incrementAndGet();
            }
        }

        @Override public long count() { return count.get(); }
        @Override public long sum() { return sum.get(); }
        @Override public int min() { return min.get(); }
        @Override public int max() { return max.get(); }
    }

    /** Same thing with accumulateAndGet — the CAS loop lives inside the JDK. */
    static final class AccumulateStats implements Stats {
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong sum = new AtomicLong();
        private final AtomicInteger min = new AtomicInteger(Integer.MAX_VALUE);
        private final AtomicInteger max = new AtomicInteger(Integer.MIN_VALUE);

        @Override public void record(int value) {
            count.incrementAndGet();
            sum.addAndGet(value);
            min.accumulateAndGet(value, Math::min);
            max.accumulateAndGet(value, Math::max);
        }

        @Override public long count() { return count.get(); }
        @Override public long sum() { return sum.get(); }
        @Override public int min() { return min.get(); }
        @Override public int max() { return max.get(); }
    }

    public static void main(String[] args) throws InterruptedException {
        final int threads = 8;
        final int perThread = 100_000;
        int[][] input = new int[threads][perThread];
        for (var row : input) {
            for (int i = 0; i < row.length; i++) {
                row[i] = ThreadLocalRandom.current().nextInt();
            }
        }
        long expectedCount = (long) threads * perThread;
        long expectedSum = Arrays.stream(input).flatMapToInt(Arrays::stream).asLongStream().sum();
        int expectedMin = Arrays.stream(input).flatMapToInt(Arrays::stream).min().orElseThrow();
        int expectedMax = Arrays.stream(input).flatMapToInt(Arrays::stream).max().orElseThrow();

        var cas = new CasStats();
        run(cas, input);
        verify("CAS loop", cas, expectedCount, expectedSum, expectedMin, expectedMax);
        System.out.println("  CAS retries: " + cas.casRetries.get());

        var accumulate = new AccumulateStats();
        run(accumulate, input);
        verify("accumulateAndGet", accumulate, expectedCount, expectedSum, expectedMin, expectedMax);

        System.out.println("Ex22LockFreeStats finished");
    }

    private static void run(Stats stats, int[][] input) throws InterruptedException {
        var workers = new Thread[input.length];
        for (int i = 0; i < workers.length; i++) {
            int[] row = input[i];
            workers[i] = new Thread(() -> {
                for (int value : row) {
                    stats.record(value);
                }
            });
            workers[i].start();
        }
        for (var worker : workers) {
            worker.join();
        }
    }

    private static void verify(String label, Stats stats, long count, long sum, int min, int max) {
        boolean ok = stats.count() == count && stats.sum() == sum && stats.min() == min && stats.max() == max;
        System.out.printf(Locale.ROOT, "  %-18s count=%d sum=%d min=%d max=%d -> %s%n",
                label, stats.count(), stats.sum(), stats.min(), stats.max(), ok ? "OK" : "MISMATCH");
    }
}
