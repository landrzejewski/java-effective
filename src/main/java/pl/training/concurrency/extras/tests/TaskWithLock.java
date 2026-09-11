package pl.training.concurrency.extras.tests;

import java.util.concurrent.locks.Lock;

/**
 * A task that repeatedly takes a lock and holds it briefly — used by {@code TaskWithLockTest} to watch a
 * {@link pl.training.concurrency.extras.common.TestLock} from the outside while it is under contention.
 */
public class TaskWithLock implements Runnable {

    private static final int ITERATIONS = 10;
    private static final long HOLD_MILLIS = 20;

    private final Lock lock;

    public TaskWithLock(Lock lock) {
        this.lock = lock;
    }

    @Override
    public void run() {
        for (int index = 0; index < ITERATIONS; index++) {
            lock.lock();
            try {
                Thread.sleep(HOLD_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                // finally, not "after the try": an exception between lock() and unlock()
                // would otherwise leak the lock forever (Mod004 §1).
                lock.unlock();
            }
        }
    }

    public static int totalIterations(int threads) {
        return threads * ITERATIONS;
    }

    public static long holdMillis() {
        return HOLD_MILLIS;
    }
}
