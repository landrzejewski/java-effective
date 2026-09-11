package pl.training.concurrency.extras.tests;

import java.util.Locale;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A unit test that observes lost updates on {@link CounterWithRaceCondition} (Mod002 §1).
 *
 * <p>Note the shape of the assertion. Writing {@code assertEquals(expected, counter.getCount())} is tempting, but
 * such a test is flaky by construction: with a bit of luck the interleavings do not overlap and the counter comes
 * out correct. A race can be observed; it cannot be guaranteed.
 *
 * <p>So the test asserts an INVARIANT that always holds: the counter can never exceed the number of increments,
 * and any shortfall is lost updates. For actually hunting races, use jcstress
 * (src/jcstress/java/.../CounterTest.java).
 */
class LostUpdateTest {

    private static final int THREADS = 8;
    private static final int INCREMENTS_PER_THREAD = 100_000;

    @Test
    void unsynchronizedIncrementLosesUpdates() throws InterruptedException {
        var counter = new CounterWithRaceCondition();
        var startGate = new CountDownLatch(1);
        var finished = new CountDownLatch(THREADS);

        try (var executor = Executors.newFixedThreadPool(THREADS)) {
            for (int index = 0; index < THREADS; index++) {
                executor.execute(() -> {
                    try {
                        startGate.await();              // release all threads at once, to maximise collisions
                        for (int i = 0; i < INCREMENTS_PER_THREAD; i++) {
                            counter.increment();
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finished.countDown();
                    }
                });
            }
            startGate.countDown();
            finished.await();
        }

        int expected = THREADS * INCREMENTS_PER_THREAD;
        int actual = counter.getCount();
        System.out.printf(Locale.ROOT, "expected: %,d, actual: %,d, lost: %,d%n",
                expected, actual, expected - actual);

        // Invariant: increments can be lost, but they can never multiply.
        assertTrue(actual <= expected, "the counter must never exceed the number of increments");
        assertTrue(actual > 0, "at least some increments must be visible");
    }
}
