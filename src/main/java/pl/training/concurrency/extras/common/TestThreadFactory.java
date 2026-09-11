package pl.training.concurrency.extras.common;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A thread factory that gives pool threads meaningful names.
 *
 * <p>This is the single highest-value thing you can do for future-you: a thread dump full of "pool-1-thread-7"
 * tells you nothing, while "report-worker-7" tells you which subsystem is stuck (Mod007 §8).
 */
public class TestThreadFactory implements ThreadFactory {

    private static final String DEFAULT_PREFIX = "T";

    private final AtomicLong counter = new AtomicLong();
    private final String prefix;
    private final boolean daemon;

    public TestThreadFactory() {
        this(DEFAULT_PREFIX, false);
    }

    public TestThreadFactory(String prefix, boolean daemon) {
        this.prefix = prefix;
        this.daemon = daemon;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        var thread = new Thread(runnable, prefix + counter.incrementAndGet());
        thread.setDaemon(daemon);
        // Priorities are deliberately left at the default. The HotSpot JVM maps them loosely onto OS
        // priorities and offers no priority inheritance, so relying on them for correctness does not
        // work (Mod014, "Priority inversion").
        return thread;
    }
}
