package pl.training.concurrency.extras.solution2;

import java.util.concurrent.ThreadLocalRandom;

public class Producer implements Runnable {

    private final Factory factory;
    private final String atomName;
    private final int requiredNumber;
    private final int atomsToProduce;

    public Producer(Factory factory, String atomName, int requiredNumber, int atomsToProduce) {
        this.factory = factory;
        this.atomName = atomName;
        this.requiredNumber = requiredNumber;
        this.atomsToProduce = atomsToProduce;
    }

    @Override
    public void run() {
        try {
            for (int index = 0; index < atomsToProduce; index++) {
                factory.atom(atomName, requiredNumber);
                Thread.sleep(ThreadLocalRandom.current().nextInt(20));
            }
        } catch (InterruptedException exception) {
            // Restore the flag and wind down — never swallow a cancellation request (Mod001 §8).
            Thread.currentThread().interrupt();
        }
    }
}
