package pl.training.concurrency.extras.solution5;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A bounded blocking queue built on a {@link ReentrantLock} with two {@link Condition}s — the hand-written version
 * of {@link java.util.concurrent.ArrayBlockingQueue} (Mod004 §2, Mod005 §4).
 *
 * <p>Two rules this class exists to demonstrate:
 * <ul>
 *   <li>{@code signal()} may only be called while the associated lock is HELD. Signalling after {@code unlock()}
 *       throws {@link IllegalMonitorStateException}.</li>
 *   <li>Every {@code lock()} needs a matching {@code finally { unlock(); }}. Without it an interrupted
 *       {@code await()} propagates out with the lock still held, and the queue is dead forever.</li>
 * </ul>
 */
public class LockBlockingQueue<T> {

    private final T[] array;
    private final int capacity;
    private final Lock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();

    private int size;
    private int head;
    private int tail;

    @SuppressWarnings("unchecked")
    public LockBlockingQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.array = (T[]) new Object[capacity];
        this.capacity = capacity;
    }

    public void enqueue(T item) throws InterruptedException {
        lock.lock();
        try {
            while (size == capacity) {
                notFull.await();
            }
            array[tail] = item;
            tail = (tail + 1) % capacity;
            size++;
            notEmpty.signal();   // still holding the lock — required
        } finally {
            lock.unlock();
        }
    }

    public T dequeue() throws InterruptedException {
        lock.lock();
        try {
            while (size == 0) {
                notEmpty.await();
            }
            T item = array[head];
            array[head] = null;  // do not keep a dead reference alive
            head = (head + 1) % capacity;
            size--;
            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return size;
        } finally {
            lock.unlock();
        }
    }
}
