package pl.training.concurrency.extras.solution5;

/**
 * The same bounded queue as {@link LockBlockingQueue}, built on an intrinsic monitor instead (Mod003 §4).
 *
 * <p>One monitor serves two predicates ("not full" and "not empty"), so every state change must use
 * {@code notifyAll()} — {@code notify()} could wake a producer when only consumers can make progress.
 */
public class MonitorBlockingQueue<T> {

    private final T[] array;
    private final int capacity;
    private final Object lock = new Object();

    private int size;
    private int head;
    private int tail;

    @SuppressWarnings("unchecked")
    public MonitorBlockingQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.array = (T[]) new Object[capacity];
        this.capacity = capacity;
    }

    public void enqueue(T item) throws InterruptedException {
        synchronized (lock) {
            while (size == capacity) {
                lock.wait();
            }
            array[tail] = item;
            tail = (tail + 1) % capacity;
            size++;
            lock.notifyAll();
        }
    }

    public T dequeue() throws InterruptedException {
        synchronized (lock) {
            while (size == 0) {
                lock.wait();
            }
            T item = array[head];
            array[head] = null;
            head = (head + 1) % capacity;
            size--;
            lock.notifyAll();
            return item;
        }
    }

    public int size() {
        synchronized (lock) {
            return size;
        }
    }
}
