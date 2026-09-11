package pl.training.concurrency.extras.tests;

import org.junit.jupiter.api.Test;
import pl.training.concurrency.extras.common.TestExecutor;
import pl.training.concurrency.extras.common.TestPhaser;
import pl.training.concurrency.extras.common.TestThreadFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multi-phase synchronization with a {@link java.util.concurrent.Phaser} (Mod006 §4).
 *
 * <p>The earlier version slept for 100 seconds and asserted nothing, which stalled the whole {@code mvn test}
 * build. It now waits for the tasks to actually finish and verifies that the phaser advanced through exactly the
 * planned number of phases, and that it terminated by itself once every party had deregistered.
 */
class TaskWithPhasesTest {

    private static final int PARTIES = 3;
    private static final int PHASES = 4;

    @Test
    void allPartiesAdvanceThroughEveryPhaseAndThenTerminate() throws InterruptedException {
        var phaser = new TestPhaser(PARTIES);
        var executor = new TestExecutor(PARTIES, PARTIES, 10, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10));
        executor.setThreadFactory(new TestThreadFactory());

        for (int index = 0; index < PARTIES; index++) {
            executor.execute(new TaskWithPhases(PHASES, phaser));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "tasks must finish well within the timeout");
        executor.printSummary();

        assertEquals(PARTIES, executor.completedTasks());
        assertEquals(0, executor.failedTasks());
        // PHASES advances for the work, plus one final advance when the last party deregisters.
        // Do NOT assert on getPhase() here: after termination it returns a negative sentinel.
        assertEquals(PHASES + 1, phaser.advances(),
                "one advance per phase, plus the final advance when the last party deregisters");
        assertTrue(phaser.isTerminated(), "every party deregisters, so the phaser must terminate");
    }
}
