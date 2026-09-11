package pl.training.concurrency.extras.common;

import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@link ThreadPoolExecutor} that times every task, to show what the {@code beforeExecute} / {@code afterExecute}
 * hooks are for. Mod007 §8 covers configuring a ThreadPoolExecutor by hand; these two protected hooks are the
 * extension point it does not go into.
 *
 * <p>The earlier version keyed its timings on {@code runnable.hashCode()} and stored the start timestamp and the
 * duration in the same map, so a hash collision — or a task submitted through {@code submit()}, which wraps it in
 * a FutureTask — produced nonsense or a NullPointerException. Timing state now lives in a ThreadLocal, which is
 * exactly the right tool here: beforeExecute and afterExecute always run on the same worker thread.
 */
public class TestExecutor extends ThreadPoolExecutor {

    private final ThreadLocal<Long> startedAt = new ThreadLocal<>();
    private final ConcurrentLinkedQueue<Long> durations = new ConcurrentLinkedQueue<>();
    private final AtomicLong failures = new AtomicLong();

    public TestExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                        BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    @Override
    protected void beforeExecute(Thread thread, Runnable runnable) {
        super.beforeExecute(thread, runnable);
        startedAt.set(System.nanoTime());
    }

    @Override
    protected void afterExecute(Runnable runnable, Throwable throwable) {
        Long start = startedAt.get();
        if (start != null) {
            durations.add(System.nanoTime() - start);
            startedAt.remove();
        }
        if (throwable != null) {
            failures.incrementAndGet();
        }
        super.afterExecute(runnable, throwable);
    }

    /** Tasks that have finished so far. */
    public int completedTasks() {
        return durations.size();
    }

    public long failedTasks() {
        return failures.get();
    }

    public long averageDurationMillis() {
        return durations.stream().mapToLong(Long::longValue).sum() / Math.max(1, durations.size())
                / 1_000_000;
    }

    public void printSummary() {
        System.out.printf(Locale.ROOT, "  tasks completed=%d, failed=%d, average duration=%d ms%n",
                completedTasks(), failedTasks(), averageDurationMillis());
    }
}
