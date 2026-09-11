package pl.training.concurrency.extras.solution2;

import java.util.Arrays;
import java.util.Collections;

/**
 * The H2O problem: assemble molecules from atoms produced by independent threads. A molecule is complete once two
 * hydrogen atoms and one oxygen atom have arrived; the bonding thread prints it and resets the slots.
 *
 * <p>The guard is per-element: a hydrogen thread waits while two hydrogens are already staged, an oxygen thread
 * waits while one oxygen is. That is what makes {@code count == 3} imply exactly H, H, O.
 */
public class Factory {

    private static final int MOLECULE_SIZE = 3;

    private final Object monitor = new Object();
    private final String[] molecule = new String[MOLECULE_SIZE];
    private int count;
    private int moleculesProduced;

    public void atom(String name, int requiredNumber) throws InterruptedException {
        synchronized (monitor) {
            // while, not if: the predicate must be re-checked after every wake-up (Mod003 §4).
            while (Collections.frequency(Arrays.asList(molecule), name) == requiredNumber) {
                monitor.wait();
            }
            molecule[count++] = name;
            if (count == MOLECULE_SIZE) {
                produce();
            }
            monitor.notifyAll();
        }
    }

    public int moleculesProduced() {
        synchronized (monitor) {
            return moleculesProduced;
        }
    }

    private void produce() {
        System.out.println("  produced " + String.join("", molecule));
        Arrays.fill(molecule, null);
        count = 0;
        moleculesProduced++;
    }
}
