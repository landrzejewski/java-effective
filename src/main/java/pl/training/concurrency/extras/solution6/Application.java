package pl.training.concurrency.extras.solution6;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ThreadLocalRandom;

public class Application {

    private static final int FIRST_GROUP = 8;
    private static final int SECOND_GROUP = 8;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("[solution6] ride sharing: " + FIRST_GROUP + " + " + SECOND_GROUP + " riders");

        var taxiManager = new TaxiManager();
        List<Thread> riders = new ArrayList<>();     // a List, not a Set: start order should be reproducible

        for (int index = 0; index < FIRST_GROUP; index++) {
            riders.add(rider("first-" + (index + 1), taxiManager::seatFirstGroup));
        }
        for (int index = 0; index < SECOND_GROUP; index++) {
            riders.add(rider("second-" + (index + 1), taxiManager::seatSecondGroup));
        }

        // Stagger the ARRIVALS. The earlier version slept while merely creating the threads and then
        // started them all at once, so the delays did nothing at all.
        for (var rider : riders) {
            rider.start();
            Thread.sleep(ThreadLocalRandom.current().nextInt(40));
        }

        for (var rider : riders) {
            rider.join(2_000);
        }

        int total = FIRST_GROUP + SECOND_GROUP;
        int stranded = total - taxiManager.seatedRiders();
        System.out.println("  rides completed = " + taxiManager.ridesCompleted()
                + ", riders seated = " + taxiManager.seatedRiders() + "/" + total);
        if (stranded > 0) {
            // Inherent to the problem, not a bug: a leftover of 3+1 can never form a legal car.
            // The riders are daemon threads, so the JVM still exits.
            System.out.println("  " + stranded + " rider(s) stranded — no legal 4/0 or 2/2 combination left");
        }
    }

    private interface Boarding {
        void board() throws InterruptedException, BrokenBarrierException;
    }

    private static Thread rider(String name, Boarding boarding) {
        var thread = new Thread(() -> {
            try {
                boarding.board();
            } catch (InterruptedException | BrokenBarrierException exception) {
                Thread.currentThread().interrupt();
            }
        }, name);
        thread.setDaemon(true);   // a stranded rider must not keep the JVM alive
        return thread;
    }
}
