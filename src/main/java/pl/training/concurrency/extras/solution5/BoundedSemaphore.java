package pl.training.concurrency.extras.solution5;

/**
 * A counting semaphore built from scratch on an intrinsic monitor — the teaching counterpart of
 * {@link java.util.concurrent.Semaphore} (Mod006 §1).
 *
 * <p>Two details that are easy to get wrong:
 * <ul>
 *   <li>{@code acquire()} must NOT notify. It consumes a permit; waking another waiter only produces a spurious
 *       wake-up that immediately goes back to waiting.</li>
 *   <li>{@code release()} uses {@code notifyAll()}, not {@code notify()}. A single monitor serves every waiter, and
 *       waking the wrong one is a lost-wakeup bug (Mod003 §4).</li>
 * </ul>
 */
public class BoundedSemaphore {

    private final int maxPermits;
    private int usedPermits;

    public BoundedSemaphore(int maxPermits) {
        this(maxPermits, maxPermits);
    }

    public BoundedSemaphore(int maxPermits, int initialPermits) {
        if (maxPermits <= 0) {
            throw new IllegalArgumentException("maxPermits must be positive");
        }
        if (initialPermits < 0 || initialPermits > maxPermits) {
            throw new IllegalArgumentException("initialPermits must be within [0, maxPermits]");
        }
        this.maxPermits = maxPermits;
        this.usedPermits = maxPermits - initialPermits;
    }

    public synchronized void acquire() throws InterruptedException {
        while (usedPermits == maxPermits) {
            wait();
        }
        usedPermits++;
    }

    public synchronized void release() {
        if (usedPermits == 0) {
            throw new IllegalStateException("release() without a matching acquire()");
        }
        usedPermits--;
        notifyAll();
    }

    public synchronized int availablePermits() {
        return maxPermits - usedPermits;
    }
}
