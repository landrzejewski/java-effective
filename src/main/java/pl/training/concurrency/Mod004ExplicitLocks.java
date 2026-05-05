package pl.training.concurrency;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.StampedLock;

/*
When NOT to use explicit locks

- For a thread-safe map: prefer ConcurrentHashMap (Mod005). The library already does fine-grained locking internally
  and exposes atomic compound operations (compute, merge, computeIfAbsent).
- For coordination of independent counters: prefer LongAdder / AtomicInteger (Mod002) — no lock at all.
- For producer–consumer: prefer a BlockingQueue (Mod005). The queue subsumes the locking and the condition signalling.
- For coordinating a fan-out of tasks: prefer StructuredTaskScope (Mod012). It makes cancellation and aggregation
  explicit instead of hidden in a lock protocol.

A good rule of thumb: reach for an explicit lock only when no higher-level abstraction in java.util.concurrent covers
the case. The library version is almost always faster and easier to reason about.
*/

final class BoundedQueueWithConditions<T> {
    private final Deque<T> items = new ArrayDeque<>();
    private final int capacity;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final Condition notEmpty = lock.newCondition();

    BoundedQueueWithConditions(int capacity) { this.capacity = capacity; }

    public void put(T item) throws InterruptedException {
        lock.lock();
        try {
            while (items.size() == capacity) notFull.await();
            items.addLast(item);
            notEmpty.signal(); // signal exactly one consumer
        } finally { lock.unlock(); }
    }

    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (items.isEmpty()) notEmpty.await();
            T item = items.removeFirst();
            notFull.signal(); // signal exactly one producer
            return item;
        } finally { lock.unlock(); }
    }
}

final class StampedCache {
    private final Map<String, Integer> map = new HashMap<>();
    private final StampedLock lock = new StampedLock();

    Integer get(String key) {
        long stamp = lock.tryOptimisticRead();
        Integer value = map.get(key);             // unsafe read — must validate
        if (!lock.validate(stamp)) {              // a writer ran during our read
            stamp = lock.readLock();
            try { value = map.get(key); }
            finally { lock.unlockRead(stamp); }
        }
        return value;
    }

    void put(String key, Integer value) {
        long stamp = lock.writeLock();
        try { map.put(key, value); }
        finally { lock.unlockWrite(stamp); }
    }
}

public final class Mod004ExplicitLocks {

    private Mod004ExplicitLocks() {}

    /*
    ReentrantLock basics

    - ReentrantLock is the explicit, library-level alternative to the synchronized keyword. The semantics are similar
      — same thread can re-enter, monitor-style mutual exclusion, happens-before on unlock — but the API is much
      richer.
    - The mandatory shape is lock(); try { ... } finally { unlock(); }. If you forget the finally, an exception in the
      critical section leaks the lock and every other thread blocks forever.
    - Pros over synchronized: timeout, interruptible acquisition, multiple condition queues per lock, fairness, and
      rich introspection (getOwner, getQueuedThreads, getQueueLength).
    - Cons: more verbose; failure to call unlock is silent; you cannot use try-with-resources directly. In practice,
      prefer synchronized for simple mutual exclusion and ReentrantLock only when you need a feature it provides.
    */
    static void reentrantLockBasics() throws InterruptedException {
        System.out.println("[Section 1] ReentrantLock basics");

        var lock = new ReentrantLock();
        Runnable critical = () -> {
            lock.lock();
            try {
                System.out.println("  enter " + Thread.currentThread().getName()
                        + ", hold count=" + lock.getHoldCount()
                        + ", queueLen=" + lock.getQueueLength());
                try { Thread.sleep(50); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } finally {
                lock.unlock();
            }
        };
        var t1 = new Thread(critical, "A");
        var t2 = new Thread(critical, "B");
        t1.start(); t2.start();
        t1.join(); t2.join();
    }

    /*
    Multiple Conditions on one lock

    - An intrinsic monitor has only one wait set; you must notifyAll and let waiters re-check their predicates. Under
      heavy load, this wakes too many threads.
    - lock.newCondition() creates a separate wait queue tied to the same lock. You can give producers and consumers
      their own conditions ("not full", "not empty"), and signal only the relevant side.
    - Same scaffold as wait/notify: await() releases the lock and parks; signal() or signalAll() wakes one or all
      waiters. Just like wait, awaits must be in a while loop because of spurious wakeups.
    */
    static void twoConditionsPC() throws InterruptedException {
        System.out.println("[Section 2] producer–consumer with two Conditions");

        var queue = new BoundedQueueWithConditions<String>(2);
        var producer = Thread.ofPlatform().name("producer").start(() -> {
            try {
                for (int i = 0; i < 4; i++) {
                    queue.put("doc-" + i);
                    System.out.println("  put doc-" + i);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        var consumer = Thread.ofPlatform().name("consumer").start(() -> {
            try {
                Thread.sleep(40);
                for (int i = 0; i < 4; i++) {
                    System.out.println("  got " + queue.take());
                    Thread.sleep(20);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        producer.join();
        consumer.join();
    }

    /*
    tryLock and tryLock(timeout)

    - lock.tryLock() attempts to acquire the lock and returns true on success or false immediately if it is held by
      someone else. Use it when you want a fallback path instead of blocking — for example, "skip this update if a
      snapshot is in progress".
    - lock.tryLock(timeout, unit) waits up to timeout. Returns false on timeout, throws InterruptedException if
      interrupted. This is the building block for deadlock-resistant code: acquire each lock with a small timeout and
      back off if both locks cannot be obtained.
    */
    static void tryLockWithTimeout() throws InterruptedException {
        System.out.println("[Section 3] tryLock with timeout");

        var lock = new ReentrantLock();
        // Holder grabs the lock for 200ms.
        var holder = Thread.ofPlatform().name("holder").start(() -> {
            lock.lock();
            try { Thread.sleep(200); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { lock.unlock(); }
        });
        Thread.sleep(20);

        // Contender waits up to 50ms — fails, takes the fallback path.
        boolean acquiredFast = lock.tryLock(50, TimeUnit.MILLISECONDS);
        System.out.println("  tryLock(50ms) -> " + acquiredFast + " (fallback path)");
        if (acquiredFast) lock.unlock();

        // Contender waits up to 500ms — succeeds.
        boolean acquiredSlow = lock.tryLock(500, TimeUnit.MILLISECONDS);
        System.out.println("  tryLock(500ms) -> " + acquiredSlow);
        if (acquiredSlow) lock.unlock();
        holder.join();
    }

    /*
    Fairness

    - A fair lock grants the lock in FIFO order: the longest-waiting thread always wins. Construct one with
      new ReentrantLock(true).
    - A non-fair lock (the default) lets a newly arriving thread "barge" past the queue if it happens to find the lock
      free. This is faster on average — barging keeps cores busy — but a thread can starve indefinitely under
      contention.
    - Use fairness when starvation is observable and the throughput cost is acceptable; use the default everywhere
      else.
    */
    static void fairness() throws InterruptedException {
        System.out.println("[Section 4] fairness");

        var fair = new ReentrantLock(true);
        var order = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        Runnable body = () -> {
            for (int i = 0; i < 3; i++) {
                fair.lock();
                try {
                    order.add(Thread.currentThread().getName());
                    Thread.sleep(5);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                finally { fair.unlock(); }
            }
        };
        var a = new Thread(body, "A");
        var b = new Thread(body, "B");
        var c = new Thread(body, "C");
        a.start(); b.start(); c.start();
        a.join(); b.join(); c.join();
        System.out.println("  fair acquisition order: " + order);
    }

    /*
    ReentrantReadWriteLock

    - A read–write lock has two underlying locks: any number of threads can hold the read lock simultaneously, but the
      write lock is exclusive against both readers and other writers.
    - Best for read-heavy workloads. Under read-mostly traffic, readers run in parallel; under write-heavy traffic the
      read–write protocol adds overhead with no payoff.
    - Same lock()/unlock() shape, but you must take the right side: readLock() or writeLock(). A reader cannot upgrade
      to a writer atomically (you must release the read lock first), but a writer holding the write lock can downgrade
      by acquiring the read lock and then releasing the write lock.
    - The API is older than StampedLock and lacks optimistic reads — see §6.
    */
    static void readWriteLock() throws InterruptedException {
        System.out.println("[Section 5] ReentrantReadWriteLock");

        var rwLock = new ReentrantReadWriteLock();
        var store = new HashMap<String, Integer>();
        store.put("a", 1);

        Runnable reader = () -> {
            rwLock.readLock().lock();
            try { System.out.println("  reader sees a=" + store.get("a")); }
            finally { rwLock.readLock().unlock(); }
        };
        Runnable writer = () -> {
            rwLock.writeLock().lock();
            try { store.put("a", store.get("a") + 1); System.out.println("  writer incremented a"); }
            finally { rwLock.writeLock().unlock(); }
        };

        // Concurrent readers + occasional writers.
        Thread r1 = new Thread(reader), r2 = new Thread(reader), w = new Thread(writer);
        r1.start(); r2.start(); w.start();
        r1.join(); r2.join(); w.join();

        System.out.println("  final a=" + store.get("a"));
    }

    /*
    StampedLock and optimistic reads

    - StampedLock (Java 8+) provides three modes: write, read, and optimistic read. Each lock/unlock returns a long
      stamp used to release or validate.
    - Optimistic read: call tryOptimisticRead(), snapshot the fields, then call validate(stamp). If no writer has
      acquired the lock in between, your snapshot is consistent and you skipped the read-lock cost entirely. Otherwise
      you fall back to the conventional readLock().
    - StampedLock is not reentrant and does not support Condition. Treat it as a high-performance specialist for
      read-mostly hot paths, not a drop-in replacement for ReentrantReadWriteLock.
    - tryConvertToWriteLock(stamp) lets you upgrade a read stamp to a write stamp without releasing first; it can
      return 0 if conversion is impossible, in which case you release and acquire the write lock conventionally.
    */
    static void stampedLock() throws InterruptedException {
        System.out.println("[Section 6] StampedLock");

        var cache = new StampedCache();
        cache.put("x", 1);

        // 8 readers + 1 writer for a brief burst.
        var readers = new Thread[8];
        for (int i = 0; i < readers.length; i++) {
            readers[i] = new Thread(() -> {
                long start = System.nanoTime();
                int hits = 0;
                while (System.nanoTime() - start < 100_000_000L) { // 100ms
                    if (cache.get("x") != null) hits++;
                }
                System.out.println("  reader hits=" + hits);
            });
        }
        var writer = new Thread(() -> {
            long start = System.nanoTime();
            int writes = 0;
            while (System.nanoTime() - start < 100_000_000L) {
                cache.put("x", writes);
                writes++;
                try { Thread.sleep(2); } catch (InterruptedException e) { return; }
            }
            System.out.println("  writes=" + writes);
        });

        for (var r : readers) r.start();
        writer.start();
        for (var r : readers) r.join();
        writer.join();
    }

    public static void main(String[] args) throws InterruptedException {
        reentrantLockBasics();
        twoConditionsPC();
        tryLockWithTimeout();
        fairness();
        readWriteLock();
        stampedLock();
        System.out.println("Mod004ExplicitLocks finished");
    }
}
