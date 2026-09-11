package pl.training.concurrency.extras.common;

import java.io.Serial;
import java.util.Collection;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A {@link ReentrantLock} that exposes its protected monitoring methods.
 *
 * <p>These are diagnostics, not synchronisation primitives: every value they return is a snapshot that may already
 * be stale by the time you read it. In particular {@link #getOwner()} can return null the instant after
 * {@link #hasQueuedThreads()} returned true — see {@code TaskWithLockTest} for how to read them defensively
 * (Mod001 §4).
 */
public class TestLock extends ReentrantLock {

    @Serial
    private static final long serialVersionUID = 1L;

    public Collection<Thread> getLockedThreads() {
        return getQueuedThreads();
    }

    @Override
    public Thread getOwner() {
        return super.getOwner();
    }
}
