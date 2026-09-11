package pl.training.concurrency.extras.tests;

/**
 * A counter with a deliberate race, used by {@code LostUpdateTest} and by the jcstress test in src/jcstress/java.
 *
 * <p>{@code count++} looks atomic but compiles to three separate steps — load, add, store. Two threads can both
 * load 7, both compute 8, and both store 8: the counter advances by one instead of two (Mod002 §1).
 */
public class CounterWithRaceCondition {

    private int count;

    /** INCORRECT: read-modify-write without synchronisation. */
    public void increment() {
        count++;
    }

    public int getCount() {
        return count;
    }
}
