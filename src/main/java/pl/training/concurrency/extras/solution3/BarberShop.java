package pl.training.concurrency.extras.solution3;

import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The sleeping-barber problem, coordinated with four semaphores that encode the hand-off protocol between the
 * barber and one customer at a time.
 *
 * <p>Every {@code lock()} below is paired with a {@code finally { unlock(); }}. The earlier version unlocked on the
 * happy path only, so any exception between lock and unlock wedged the whole shop permanently — the single most
 * common way an explicit lock turns into a deadlock (Mod004 §1).
 */
public class BarberShop {

    private final Semaphore waitForCustomerToEnter = new Semaphore(0);
    private final Semaphore waitForBarberToGetReady = new Semaphore(0);
    private final Semaphore waitForCustomerToLeave = new Semaphore(0);
    private final Semaphore waitForBarberToCutHair = new Semaphore(0);
    private final ReentrantLock lock = new ReentrantLock();
    private final int chairs;

    private int waitingCustomers;
    private int hairCutsGiven;
    private int customersTurnedAway;

    public BarberShop(int chairs) {
        this.chairs = chairs;
    }

    /** @return true if the customer got a haircut, false if the waiting room was full */
    boolean customerWalksIn() throws InterruptedException {
        lock.lock();
        try {
            if (waitingCustomers == chairs) {
                customersTurnedAway++;
                System.out.println("  " + Thread.currentThread().getName()
                        + " walks out, all " + chairs + " chairs occupied");
                return false;
            }
            waitingCustomers++;
        } finally {
            lock.unlock();
        }

        waitForCustomerToEnter.release();   // let the barber know a client is here
        waitForBarberToGetReady.acquire();  // wait for our turn

        lock.lock();
        try {
            waitingCustomers--;             // the chair in the waiting area is free again
        } finally {
            lock.unlock();
        }

        waitForBarberToCutHair.acquire();   // wait until the haircut is done
        waitForCustomerToLeave.release();   // leave the shop
        return true;
    }

    /** Runs until interrupted — the caller cancels it once the last customer has been served. */
    void barber() throws InterruptedException {
        while (!Thread.currentThread().isInterrupted()) {
            waitForCustomerToEnter.acquire();
            waitForBarberToGetReady.release();
            System.out.println("  barber cutting hair... " + (++hairCutsGiven));
            Thread.sleep(200);
            waitForBarberToCutHair.release();
            waitForCustomerToLeave.acquire();
        }
    }

    int hairCutsGiven() {
        return hairCutsGiven;
    }

    int customersTurnedAway() {
        lock.lock();
        try {
            return customersTurnedAway;
        } finally {
            lock.unlock();
        }
    }
}
