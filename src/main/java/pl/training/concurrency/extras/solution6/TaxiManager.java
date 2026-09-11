package pl.training.concurrency.extras.solution6;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The "ride sharing" problem: a car holds exactly four passengers, and a ride may only leave with a politically
 * acceptable mix — four from one group, or two and two. Never three-and-one.
 *
 * <p>Shape of the solution:
 * <ul>
 *   <li>A mutex guards the two arrival counters.</li>
 *   <li>Whoever completes a valid combination becomes the RIDE LEADER: it releases the right number of permits to
 *       wake its passengers and — deliberately — keeps holding the mutex across the barrier and the ride, so no
 *       later arrival can be counted into a car that is already leaving.</li>
 *   <li>A {@link CyclicBarrier} of four makes all passengers board before the leader drives off, and is reusable
 *       for the next ride.</li>
 * </ul>
 *
 * <p>Every lock acquisition is paired with a {@code finally}. That matters more here than anywhere else in this
 * package: the leader holds the mutex across {@link CyclicBarrier#await()}, which can throw
 * {@link BrokenBarrierException}. Without the finally, one broken barrier would strand every future rider.
 */
public class TaxiManager {

    private final CyclicBarrier barrier = new CyclicBarrier(4);
    private final ReentrantLock lock = new ReentrantLock();
    private final Semaphore firstGroup = new Semaphore(0);
    private final Semaphore secondGroup = new Semaphore(0);
    private final AtomicInteger seatedRiders = new AtomicInteger();
    private final AtomicInteger ridesCompleted = new AtomicInteger();

    private int firstGroupCount;
    private int secondGroupCount;

    public void seatFirstGroup() throws InterruptedException, BrokenBarrierException {
        boolean rideLeader = false;
        lock.lock();
        try {
            firstGroupCount++;
            if (firstGroupCount == 4) {                              // four from this group
                firstGroup.release(3);
                firstGroupCount -= 4;
                rideLeader = true;
            } else if (firstGroupCount == 2 && secondGroupCount >= 2) { // two and two
                firstGroup.release(1);
                secondGroup.release(2);
                firstGroupCount -= 2;
                secondGroupCount -= 2;
                rideLeader = true;
            }
        } finally {
            if (!rideLeader) {
                lock.unlock();      // ordinary passengers release the mutex and then queue
            }
        }
        if (!rideLeader) {
            firstGroup.acquire();
        }
        board(rideLeader);
    }

    public void seatSecondGroup() throws InterruptedException, BrokenBarrierException {
        boolean rideLeader = false;
        lock.lock();
        try {
            secondGroupCount++;
            if (secondGroupCount == 4) {
                secondGroup.release(3);
                secondGroupCount -= 4;
                rideLeader = true;
            } else if (secondGroupCount == 2 && firstGroupCount >= 2) {
                secondGroup.release(1);
                firstGroup.release(2);
                secondGroupCount -= 2;
                firstGroupCount -= 2;
                rideLeader = true;
            }
        } finally {
            if (!rideLeader) {
                lock.unlock();
            }
        }
        if (!rideLeader) {
            secondGroup.acquire();
        }
        board(rideLeader);
    }

    private void board(boolean rideLeader) throws InterruptedException, BrokenBarrierException {
        try {
            System.out.println("  " + Thread.currentThread().getName() + " seated");
            seatedRiders.incrementAndGet();
            barrier.await();
            if (rideLeader) {
                ridesCompleted.incrementAndGet();
                System.out.println("  driving off (" + ridesCompleted.get() + ")");
            }
        } finally {
            if (rideLeader) {
                lock.unlock();      // the leader has held the mutex for the whole boarding
            }
        }
    }

    public int seatedRiders() {
        return seatedRiders.get();
    }

    public int ridesCompleted() {
        return ridesCompleted.get();
    }
}
