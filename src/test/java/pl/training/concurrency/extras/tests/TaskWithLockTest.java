package pl.training.concurrency.extras.tests;

import org.junit.jupiter.api.Test;
import pl.training.concurrency.extras.common.TestLock;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Observing a {@link TestLock} under contention — what {@code ReentrantLock}'s diagnostic methods are good for
 * (Mod004 §1).
 *
 * <p>The earlier version had two problems. It asserted nothing (the test method was a bare {@code sleep}), and it
 * called {@code testLock.getOwner().getName()} after checking {@code hasQueuedThreads()} — the owner could vanish
 * between the two calls, giving a {@link NullPointerException}. A monitoring snapshot has to be read defensively:
 * by definition it is already stale by the time you look at it (Mod001 §4).
 */
class TaskWithLockTest {

    private static final int THREADS = 6;

    @Test
    void contendedLockReportsQueuedThreadsAndAlwaysEndsUnlocked() throws InterruptedException {
        var testLock = new TestLock();
        var task = new TaskWithLock(testLock);

        var threads = new ArrayList<Thread>();
        for (int index = 0; index < THREADS; index++) {
            threads.add(new Thread(task, "worker-" + index));
        }
        threads.forEach(Thread::start);

        int maxQueueLengthSeen = 0;
        while (threads.stream().anyMatch(Thread::isAlive)) {
            maxQueueLengthSeen = Math.max(maxQueueLengthSeen, testLock.getLockedThreads().size());
            // Właściciel może zniknąć między dwoma wywołaniami — czytamy raz i sprawdzamy null.
            Thread owner = testLock.getOwner();
            if (owner != null) {
                assertTrue(owner.getName().startsWith("worker-"), "lock owner must be one of our workers");
            }
            Thread.sleep(10);
        }
        for (var thread : threads) {
            thread.join();
        }

        assertTrue(maxQueueLengthSeen > 0, "with " + THREADS + " workers the lock must have been contended");
        assertFalse(testLock.isLocked(), "every acquisition must be released in a finally block");
        assertEquals(0, testLock.getQueueLength(), "no thread may be left queued");
    }
}
