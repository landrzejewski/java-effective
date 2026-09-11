package pl.training.concurrency.extras.solution5;

/**
 * The same bounded queue again, this time coordinated purely with counting semaphores — no monitor, no Condition.
 *
 * <p>Three semaphores encode the whole protocol:
 * <ul>
 *   <li>{@code freeSlots} — how many items may still be written (starts full),</li>
 *   <li>{@code usedSlots} — how many items may still be read (starts empty),</li>
 *   <li>{@code mutex} — a binary semaphore protecting the array indices.</li>
 * </ul>
 *
 * <p>Acquisition ORDER matters: the counting semaphore is always taken before the mutex. Taking the mutex first
 * would let a blocked producer hold it while waiting for a consumer that needs it — a textbook deadlock.
 */
public class SemaphoreQueue<T> {

    private final T[] array;
    private final int capacity;
    private final BoundedSemaphore mutex = new BoundedSemaphore(1, 1);
    private final BoundedSemaphore freeSlots;
    private final BoundedSemaphore usedSlots;

    private int head;
    private int tail;

    @SuppressWarnings("unchecked")
    public SemaphoreQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.array = (T[]) new Object[capacity];
        this.capacity = capacity;
        this.freeSlots = new BoundedSemaphore(capacity, capacity);
        this.usedSlots = new BoundedSemaphore(capacity, 0);
    }

    public void enqueue(T item) throws InterruptedException {
        freeSlots.acquire();
        mutex.acquire();
        try {
            array[tail] = item;
            tail = (tail + 1) % capacity;
        } finally {
            mutex.release();
        }
        usedSlots.release();
    }

    public T dequeue() throws InterruptedException {
        usedSlots.acquire();
        mutex.acquire();
        T item;
        try {
            item = array[head];
            array[head] = null;
            head = (head + 1) % capacity;
        } finally {
            mutex.release();
        }
        freeSlots.release();
        return item;
    }

    public int size() {
        return capacity - freeSlots.availablePermits();
    }
}
