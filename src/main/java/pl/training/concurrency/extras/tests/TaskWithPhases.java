package pl.training.concurrency.extras.tests;

import java.util.Locale;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A worker that walks a {@link Phaser} through a fixed number of phases — used by {@code TaskWithPhasesTest} to
 * observe phase advancement from the outside.
 *
 * <p>Two things the earlier version got wrong:
 * <ul>
 *   <li>It called {@code phaser.arrive()} once before the loop. Since the party then arrived again inside the
 *       loop, the very first phase advanced without any work being done and every subsequent phase number was off
 *       by one.</li>
 *   <li>It implemented {@code Comparable<Long>} — comparing a task to a Long. {@link Comparable} is meant to order
 *       a type against itself; that declaration could never be used by any sort and has been dropped.</li>
 * </ul>
 */
public class TaskWithPhases implements Runnable {

    private static final int MAX_WORK_MILLIS = 30;

    private final int phases;
    private final Phaser phaser;

    public TaskWithPhases(int phases, Phaser phaser) {
        this.phases = phases;
        this.phaser = phaser;
    }

    @Override
    public void run() {
        try {
            for (int phase = 0; phase < phases; phase++) {
                System.out.printf(Locale.ROOT, "%s: working in phase %d%n", Thread.currentThread().getName(), phase);
                Thread.sleep(ThreadLocalRandom.current().nextInt(MAX_WORK_MILLIS));
                phaser.arriveAndAwaitAdvance();     // exactly one arrival per phase
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            phaser.arriveAndDeregister();           // leave, whatever happened
        }
    }
}
