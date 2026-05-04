package pl.training.concurrency.extras.tests;

public class CounterWithRaceCondition {
    private int count = 0;
    
    // NIEPOPRAWNE - race condition!
    public void increment() {
        count++;  // To NIE jest operacja atomowa!
    }
    
    public int getCount() {
        return count;
    }

}