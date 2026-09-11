package pl.training.concurrency.extras.common;

import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link Phaser} that logs every advance and remembers how many happened.
 *
 * <p>Why the counter exists: once a phaser terminates, {@link Phaser#getPhase()} returns a NEGATIVE value (the
 * final phase number with the sign bit set). Asserting on getPhase() after termination therefore compares against
 * something like -2147483643 rather than the phase count you expected — a small but classic trap.
 */
public class TestPhaser extends Phaser {

    private final AtomicInteger advances = new AtomicInteger();

    public TestPhaser(int parties) {
        super(parties);
    }

    @Override
    protected boolean onAdvance(int phase, int registeredParties) {
        advances.incrementAndGet();
        System.out.println("  phase " + phase + " complete, registered parties = " + registeredParties);
        return super.onAdvance(phase, registeredParties);
    }

    /** How many times the phaser advanced, including the final advance that terminates it. */
    public int advances() {
        return advances.get();
    }
}
