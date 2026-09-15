package pl.training.concurrency.exercises.mod002;

import java.util.Locale;

/**
 * Exercise 2.1 — Visibility bug hunt.
 *
 * <p>Related: Mod002 §1, §2, §3
 */
public final class Ex21VisibilityBugHunt {

    private Ex21VisibilityBugHunt() {}

    /** Plain field: the JIT may hoist the read out of the loop, so the worker may never observe stop(). */
    static final class BrokenWorker implements Runnable {
        private boolean running = true;
        private long count;

        void stop() { running = false; }

        @Override public void run() {
            while (running) {
                count++;
            }
        }
    }

    /** volatile: every read goes to main memory and every write is visible to all later reads. */
    static final class FixedWorker implements Runnable {
        private volatile boolean running = true;
        private long count;

        void stop() { running = false; }

        @Override public void run() {
            while (running) {
                count++;
            }
        }
    }

    static final class VolatileCounter {
        volatile int count;
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("[1] plain boolean flag");
        var broken = new BrokenWorker();
        runAndStop(broken, broken::stop);

        System.out.println("[2] volatile flag");
        var fixed = new FixedWorker();
        runAndStop(fixed, fixed::stop);

        System.out.println("[3] volatile does not make count++ atomic");
        var counter = new VolatileCounter();
        var threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100_000; j++) {
                    counter.count++; // read-modify-write: three steps, not one
                }
            });
            threads[i].start();
        }
        for (var thread : threads) {
            thread.join();
        }
        int expected = threads.length * 100_000;
        System.out.printf(Locale.ROOT, "  expected %d, got %d, lost %d%n",
                expected, counter.count, expected - counter.count);

        // Why synchronized would also fix visibility: a monitor release happens-before a later acquire of the same
        // monitor, so a synchronized setter + synchronized getter hand the reader the latest value.
        // Which fix also repairs count++: only synchronized (or an AtomicInteger). volatile makes each read and
        // each write visible, but two threads may still read the same old value and both write old + 1.
        // A synchronized block makes the whole read-modify-write exclusive.
        System.out.println("Ex21VisibilityBugHunt finished");
    }

    private static void runAndStop(Runnable worker, Runnable stop) throws InterruptedException {
        // daemon: a worker that never sees the flag must not keep the JVM alive
        var thread = Thread.ofPlatform().name("worker").daemon(true).start(worker);
        Thread.sleep(200);
        stop.run();
        thread.join(2000);
        System.out.println(thread.isAlive()
                ? "  worker did NOT stop within 2 s (visibility bug)"
                : "  worker stopped");
    }
}
