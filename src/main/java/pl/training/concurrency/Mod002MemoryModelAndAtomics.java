package pl.training.concurrency;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

// =================================================================================================
// Section 1: Race conditions
// =================================================================================================

/*
## Race conditions

- A **race condition** is a bug where the correctness of a program depends on the
unpredictable interleaving of threads.
- The classic case is a *read–modify–write* sequence such as `count++`. Although it
looks atomic, the JVM compiles it to three steps: load the value, add one, store the
result. Two threads can both load `7`, both compute `8`, and both store `8` — the
counter advances by 1 instead of 2.
- *Check-then-act* (`if (!map.containsKey(k)) map.put(k, v);`) is the same family of
bug: between the check and the act, another thread can change the answer.
- Race conditions disappear when one of the following holds: the operation is truly
atomic (an `Atomic*` class, or a single `volatile` write), or the section is
protected by a lock that all racing threads acquire.
*/

// =================================================================================================
// Section 2: Memory visibility
// =================================================================================================

/*
## Memory visibility

- Each CPU core has its own caches. Writes performed by one thread can sit in a
cache line and remain invisible to another thread for an unbounded amount of time
unless a synchronization mechanism flushes them.
- The JIT compiler is also allowed to **reorder** independent statements as long as
the as-if-serial rule is preserved on a single thread. From another thread's point
of view, the order of two writes can appear reversed.
- Without a happens-before relationship a reader can observe `ready == true` while
still seeing the old value of `result` — even though the writer wrote `result`
first in source order. This is the canonical "visibility problem".
- The fix is to introduce an ordering: a `volatile` write/read pair, a lock, an
`Atomic*` operation, or an `Executor` boundary.
*/

// =================================================================================================
// Section 3: Happens-before
// =================================================================================================

/*
## Happens-before

The Java Memory Model defines a partial order called *happens-before* (HB). If
action A happens-before action B, then B is guaranteed to see the effects of A.
The most useful HB rules:

1. **Program order** — within a single thread, statements happen-before later
   statements.
2. **Monitor lock** — `unlock` on a monitor happens-before any subsequent `lock` on
   the same monitor (whether `synchronized`, `ReentrantLock`, or any
   `java.util.concurrent.locks.Lock`).
3. **Volatile** — a write to a `volatile` field happens-before every subsequent
   read of that same field by any thread.
4. **Thread start/join** — `Thread.start()` happens-before any action in the new
   thread; every action in the new thread happens-before `join()` returns.
5. **Final fields** — the values written to `final` fields in a constructor are
   visible to any thread that observes the constructed object via a properly
   published reference.

If no HB chain links two actions, the JMM gives no guarantee about their relative
ordering. *No HB = no visibility.*
*/

// =================================================================================================
// Section 4: The volatile keyword
// =================================================================================================

/*
## The volatile keyword

- `volatile` guarantees **visibility** and **ordering** but NOT atomicity. A
volatile read sees the most recent volatile write to that field, and the JMM
forbids reordering loads/stores around the volatile access.
- The canonical correct use is a **stop flag**: one thread writes `true`, another
thread polls in a loop and exits when it sees `true`. Without `volatile` the JIT
can hoist the read out of the loop and the worker spins forever.
- `volatile` is wrong for any *read-modify-write* operation. `volatile int x; x++;`
is not atomic — use `AtomicInteger` instead.
- In modern Java, prefer an `Atomic*` reference or an explicit lock. `volatile` is
mostly used today for one-way flags and lazy-init double-checked-locking idioms.
*/

// =================================================================================================
// Section 5: Atomic classes
// =================================================================================================

/*
## Atomic classes

- The `java.util.concurrent.atomic` package provides lock-free wrappers around a
single value: `AtomicInteger`, `AtomicLong`, `AtomicBoolean`, `AtomicReference<T>`,
plus arrays and *FieldUpdater* variants.
- All of them expose:
  - `get()` / `set(v)` — volatile-style read/write.
  - `incrementAndGet()`, `addAndGet(d)` — atomic numeric updates.
  - `compareAndSet(expected, new)` — atomic conditional swap (CAS).
  - `updateAndGet(unaryOp)` and `accumulateAndGet(value, binaryOp)` — atomic
    transformations expressed as a lambda; the JVM retries on contention.
- Internally these are implemented with a hardware CAS instruction (`cmpxchg` on
x86, `ldxr/stxr` on ARM). They are typically faster than `synchronized` under low
contention but degrade under heavy contention because of the retry loop.
*/

// =================================================================================================
// Section 6: CAS in practice
// =================================================================================================

/*
## CAS in practice

- A CAS loop reads the current value, computes the next value, and tries
`compareAndSet`. If another thread changed the field in between, the CAS fails and
the loop retries.
- This gives lock-free progress: at least one thread always makes forward progress
on every iteration, and there are no monitors to deadlock on.
- The pattern below ("update max") is too small to need a library helper, but it
is the same skeleton as `AtomicInteger.updateAndGet(...)` internally.
*/

final class HighWaterMark {
    private final AtomicInteger max = new AtomicInteger(Integer.MIN_VALUE);

    void offer(int sample) {
        int current;
        do {
            current = max.get();
            if (sample <= current) return; // nothing to do
        } while (!max.compareAndSet(current, sample));
    }

    int peek() { return max.get(); }
}

// =================================================================================================
// Section 7: LongAdder vs AtomicLong
// =================================================================================================

/*
## LongAdder vs AtomicLong

- Under heavy contention, `AtomicLong` becomes a hotspot: every thread retries the
same memory location until its CAS succeeds, producing cache-line ping-pong.
- `LongAdder` (and `LongAccumulator`, `DoubleAdder`) maintain an array of internal
cells striped across cores. Increments hash to a cell, so most updates do not
collide. The downside is that reading the total (`sum()`) is no longer a single
atomic read — if other threads are still updating, `sum()` can return a stale
value.
- Use `LongAdder` for high-throughput counters where the consumer reads
infrequently (metrics, statistics). Use `AtomicLong` when you need a coherent
read-modify-write on a single integer (e.g., generating sequence numbers).
*/

// =================================================================================================
// Section 8: Atomic field updaters
// =================================================================================================

/*
## Atomic field updaters

- `AtomicIntegerFieldUpdater<T>`, `AtomicLongFieldUpdater<T>`,
`AtomicReferenceFieldUpdater<T,V>` perform atomic updates on **plain fields** of
existing classes via reflection.
- The motivation is memory: with millions of small objects, switching every counter
field to `AtomicInteger` adds an extra wrapper object per instance. A field
updater performs CAS directly on the underlying `volatile int`, avoiding the
allocation.
- The field must be declared `volatile` and accessible from where the updater is
created. Field updaters are an optimization — reach for them only when measurement
shows the overhead matters.
*/

final class CountedNode {
    volatile int hits; // must be volatile for the field updater
    static final AtomicIntegerFieldUpdater<CountedNode> HITS =
            AtomicIntegerFieldUpdater.newUpdater(CountedNode.class, "hits");
    void hit() { HITS.incrementAndGet(this); }
}

// =================================================================================================
// Section 9: The final field freeze
// =================================================================================================

/*
## The final field freeze

- The JMM gives `final` fields a special guarantee: when a constructor finishes,
the values written to `final` fields are visible to any thread that later sees a
reference to the constructed object — *even without synchronization*.
- This is why **immutable objects are inherently thread-safe**: an instance built
of `final` fields whose state cannot change after construction can be passed
around freely without locks.
- The guarantee only holds if the reference does not escape the constructor before
it returns. Storing `this` into a static field from inside the constructor (or
publishing it to another thread) breaks the freeze.
*/

record Point(int x, int y) {} // immutable; safe to publish freely

public final class Mod002MemoryModelAndAtomics {

    private Mod002MemoryModelAndAtomics() {}

    // --- Section 1: race conditions (lost-update demo) ---
    static void raceCondition() throws InterruptedException {
        System.out.println("[Section 1] race condition");

        class Counter { int value; void inc() { value++; } }
        var counter = new Counter();
        int threads = 8, perThread = 100_000;
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> { for (int j = 0; j < perThread; j++) counter.inc(); });
        }
        for (var t : ts) t.start();
        for (var t : ts) t.join();
        int expected = threads * perThread;
        System.out.println("  expected = " + expected + ", got = " + counter.value
                + " (lost updates = " + (expected - counter.value) + ")");
    }

    // --- Section 2: memory visibility ---
    static void memoryVisibility() throws InterruptedException {
        System.out.println("[Section 2] memory visibility");

        // Without volatile, the reader can hoist the field read out of the loop and
        // never observe ready=true. We give it a 1-second budget; on most JVMs the
        // reader will spin until the budget expires.
        class Bag { boolean ready; int result; }
        var bag = new Bag();
        var reader = new Thread(() -> {
            long deadline = System.nanoTime() + 1_000_000_000L;
            while (!bag.ready) {
                if (System.nanoTime() > deadline) {
                    System.out.println("  reader gave up waiting (no HB → flag never visible)");
                    return;
                }
            }
            System.out.println("  reader saw result = " + bag.result);
        }, "reader");
        reader.start();
        Thread.sleep(50);
        bag.result = 42;
        bag.ready = true; // no HB edge → no guarantee the reader ever sees this
        reader.join();
    }

    // --- Section 4: volatile stop flag ---
    static void volatileStopFlag() throws InterruptedException {
        System.out.println("[Section 4] volatile stop flag");

        class Worker implements Runnable {
            volatile boolean stop;
            long ticks;
            @Override public void run() {
                while (!stop) ticks++;
                System.out.println("  worker stopped after " + ticks + " ticks");
            }
        }
        var w = new Worker();
        var t = new Thread(w, "volatile-worker");
        t.start();
        Thread.sleep(50);
        w.stop = true;
        t.join();
    }

    // --- Section 5: atomic classes ---
    static void atomicClasses() throws InterruptedException {
        System.out.println("[Section 5] atomic classes");

        // (a) AtomicInteger replaces a synchronized counter with no lock.
        var hits = new AtomicInteger();
        Thread[] ts = new Thread[8];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> { for (int j = 0; j < 100_000; j++) hits.incrementAndGet(); });
        }
        for (var t : ts) t.start();
        for (var t : ts) t.join();
        System.out.println("  AtomicInteger total = " + hits.get());

        // (b) AtomicReference for an entire object.
        var lastSeen = new AtomicReference<>(new Point(0, 0));
        lastSeen.updateAndGet(p -> new Point(p.x() + 1, p.y() + 1));
        System.out.println("  AtomicReference value = " + lastSeen.get());
    }

    // --- Section 6: CAS in practice ---
    static void casLoop() throws InterruptedException {
        System.out.println("[Section 6] CAS loop");

        var hwm = new HighWaterMark();
        Thread[] producers = new Thread[6];
        for (int i = 0; i < producers.length; i++) {
            final int seed = i;
            producers[i] = new Thread(() -> {
                var rnd = new java.util.Random(seed);
                for (int j = 0; j < 1000; j++) hwm.offer(rnd.nextInt(10_000));
            });
        }
        for (var p : producers) p.start();
        for (var p : producers) p.join();
        System.out.println("  high-water mark across 6 threads = " + hwm.peek());
    }

    // --- Section 7: LongAdder vs AtomicLong ---
    static void longAdderVsAtomicLong() throws InterruptedException {
        System.out.println("[Section 7] LongAdder vs AtomicLong");

        int threads = 16, perThread = 500_000;

        var atomic = new AtomicLong();
        long t1 = System.nanoTime();
        runAndJoin(threads, () -> { for (int i = 0; i < perThread; i++) atomic.incrementAndGet(); });
        long elapsedAtomic = System.nanoTime() - t1;

        var adder = new LongAdder();
        long t2 = System.nanoTime();
        runAndJoin(threads, () -> { for (int i = 0; i < perThread; i++) adder.increment(); });
        long elapsedAdder = System.nanoTime() - t2;

        System.out.printf("  AtomicLong total=%d in %.1f ms%n",
                atomic.get(), elapsedAtomic / 1_000_000.0);
        System.out.printf("  LongAdder  total=%d in %.1f ms%n",
                adder.sum(), elapsedAdder / 1_000_000.0);
    }

    // --- Section 8: atomic field updater ---
    static void atomicFieldUpdater() throws InterruptedException {
        System.out.println("[Section 8] atomic field updater");

        var node = new CountedNode();
        runAndJoin(8, () -> { for (int i = 0; i < 100_000; i++) node.hit(); });
        System.out.println("  CountedNode.hits = " + node.hits + " (no AtomicInteger wrapper allocated)");
    }

    // --- Section 9: final field freeze ---
    static void finalFieldFreeze() throws InterruptedException {
        System.out.println("[Section 9] final field freeze");

        // We publish an immutable Point through an unsafe-looking plain field.
        // Because Point's fields are final, the receiving thread is guaranteed to
        // see fully constructed values, not zeros.
        class Holder { Point p; } // plain non-volatile reference
        var holder = new Holder();
        var observer = new Thread(() -> {
            while (holder.p == null) Thread.onSpinWait();
            Point seen = holder.p;
            // Both fields are guaranteed non-zero thanks to the final-field freeze.
            System.out.println("  observer saw fully constructed " + seen);
        });
        observer.start();
        Thread.sleep(20);
        holder.p = new Point(7, 9);
        observer.join();
    }

    // helper used by §7 and §8
    private static void runAndJoin(int threads, Runnable body) throws InterruptedException {
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) ts[i] = new Thread(body);
        for (var t : ts) t.start();
        for (var t : ts) t.join();
    }

    public static void main(String[] args) throws InterruptedException {
        raceCondition();
        memoryVisibility();
        volatileStopFlag();
        atomicClasses();
        casLoop();
        longAdderVsAtomicLong();
        atomicFieldUpdater();
        finalFieldFreeze();
        System.out.println("Mod002MemoryModelAndAtomics finished");
    }
}
