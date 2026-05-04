package pl.training.concurrency;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

// =================================================================================================
// Section 1: Race condition recap — checklist
// =================================================================================================

/*
## Race condition recap — checklist

You already saw a non-atomic increment in Mod002. Race conditions follow a
small number of recurring shapes; recognise them and reach for the appropriate
fix.

| Shape                   | Example                                  | Fix                                                |
|-------------------------|------------------------------------------|----------------------------------------------------|
| Read–modify–write       | `count++`, `total += x`                  | `Atomic*` / `synchronized`                         |
| Check-then-act          | `if (!map.containsKey(k)) map.put(k,v)`  | `ConcurrentHashMap.putIfAbsent`/`computeIfAbsent`  |
| Get-then-put on counter | `m.put(k, m.getOrDefault(k,0)+1)`        | `ConcurrentHashMap.merge(k,1L,Long::sum)`          |
| Compound state          | "two fields must be consistent"          | one lock around the whole compound update          |
*/

// =================================================================================================
// Section 2: Deadlock
// =================================================================================================

/*
## Deadlock

- Two (or more) threads each hold a lock the other is waiting for. Nobody
proceeds. Often called the *circular wait* condition.
- The minimal recipe: thread A acquires lock-1 then tries lock-2; thread B
acquires lock-2 then tries lock-1.
- A deadlocked JVM looks like a hang, but `jstack <pid>` (or `kill -3 <pid>`,
or VisualVM) reports the cycle explicitly with the message
"Found one Java-level deadlock".
*/

// =================================================================================================
// Section 3: Lock-ordering rule
// =================================================================================================

/*
## Lock-ordering rule

- The classic deadlock fix: define a global ordering on locks and always
acquire them in that order across the whole program. If every thread acquires
locks in the same sequence, no cycle can form.
- A common runtime ordering uses `System.identityHashCode(lock)` to compare
two locks at acquisition time and acquire the smaller-hashed one first. If
two locks tie, fall back to a tie-breaker mutex.
- This is the standard recipe for the "transfer money between two accounts"
operation: sort the two account locks by hash before acquiring.
*/

// =================================================================================================
// Section 4: Livelock
// =================================================================================================

/*
## Livelock

- Like deadlock, no progress is made; unlike deadlock, threads are *active* —
they keep doing something, repeatedly making moves that cancel each other.
- A typical case: two threads each `tryLock` both resources; on failure they
release everything and retry after a short wait. If their schedules align,
each retry collides again.
- Cure: add randomised jitter to the back-off, prefer `tryLock(timeout, unit)`
with an asymmetric strategy, or break the symmetry by giving one thread
priority.
*/

// =================================================================================================
// Section 5: Starvation
// =================================================================================================

/*
## Starvation

- A thread cannot make progress because other threads keep grabbing the
resource it is waiting for.
- A non-fair `ReentrantLock` (the default) lets newly arriving threads barge
past queued waiters. Under heavy contention a quiet waiter can wait forever.
- Cure: `new ReentrantLock(true)` (FIFO ordering), `Semaphore(N, true)`,
priority adjustments, or partitioning the workload so contention is local.
*/

// =================================================================================================
// Section 6: Priority inversion
// =================================================================================================

/*
## Priority inversion

- A high-priority thread waits for a lock held by a low-priority thread, while
a medium-priority thread (which neither holds nor needs the lock) keeps
preempting the low-priority holder. The high-priority thread effectively runs
at the medium priority.
- Real-time operating systems offer *priority inheritance* (the holder
temporarily inherits the waiter's priority) to fix this. The HotSpot JVM
does not — production Java rarely uses thread priorities for scheduling.
- The defensive practice is simply not to rely on priorities for correctness.
Use bounded queues, fair locks, and explicit deadlines instead.
*/

// =================================================================================================
// Section 7: Diagnostics workflow
// =================================================================================================

/*
## Diagnostics workflow

1. **Get the PID** — `ProcessHandle.current().pid()`, or `jps -l`.
2. **Take a thread dump** — `jstack <pid>`, or send the JVM SIGQUIT with
   `kill -3 <pid>`, or use VisualVM / JConsole "Thread dump".
3. **Read the dump** — look for:
   - `BLOCKED` threads → contention; check who owns the monitor.
   - `WAITING` threads with no notifier → missing `notify`/`signal`.
   - "Found one Java-level deadlock" → cycle; switch to lock-ordering.
   - `RUNNABLE` threads spinning → busy-loop or livelock.
4. **For repeating bugs**, JFR (Java Flight Recorder) records lock contention
   and parking events with timestamps — useful for investigations across many
   minutes of runtime.
*/

// =================================================================================================
// Section 8: Testing concurrent code
// =================================================================================================

/*
## Testing concurrent code

- Plain JUnit tests run sequentially; they are good for verifying a small
contention scenario but cannot prove the absence of races (the bug might
require a specific schedule that JUnit never produces).
- For race-detection tests, use the **jcstress** harness (already a dependency
of this project at `org.openjdk.jcstress:jcstress-core`). It runs your test
inside specially controlled threads, repeats it millions of times across
schedules, and reports if any unexpected outcomes occurred.
- See `src/test/java/pl/training/concurrency/e010_tests/CounterTest.java` in
this repo for a starter unit test pattern.
*/

public final class Mod014ThreadingProblems {

    private Mod014ThreadingProblems() {}

    // --- Section 2: deadlock — bounded by a watchdog so the demo does not hang ---
    static void deadlockDemo() throws InterruptedException {
        System.out.println("[Section 2] deadlock demo");

        var lockA = new Object();
        var lockB = new Object();

        Thread t1 = new Thread(() -> {
            synchronized (lockA) {
                sleep(50);
                synchronized (lockB) { /* never gets here */ }
            }
        }, "T1-A-then-B");
        Thread t2 = new Thread(() -> {
            synchronized (lockB) {
                sleep(50);
                synchronized (lockA) { /* never gets here */ }
            }
        }, "T2-B-then-A");

        t1.start();
        t2.start();
        Thread.sleep(200);

        System.out.println("  T1 state = " + t1.getState() + " (BLOCKED on lockB)");
        System.out.println("  T2 state = " + t2.getState() + " (BLOCKED on lockA)");
        System.out.println("  PID = " + ProcessHandle.current().pid()
                + "  → run `jstack " + ProcessHandle.current().pid() + "` to see the cycle");

        // Break the deadlock for the demo: stop the JVM-thread is gone in modern JDK,
        // and we cannot interrupt out of synchronized acquisition. The threads will
        // be left pending until JVM exit; we move on to the next section.
    }

    // --- Section 3: lock-ordering rule — fix the same scenario ---
    static void lockOrderingFix() throws InterruptedException {
        System.out.println("[Section 3] lock-ordering rule");

        var lockA = new Object();
        var lockB = new Object();

        Runnable transfer = () -> {
            // Sort locks by identityHashCode before acquiring — both threads now
            // acquire in the same order, so a cycle cannot form.
            Object first = lockA, second = lockB;
            if (System.identityHashCode(first) > System.identityHashCode(second)) {
                first = lockB; second = lockA;
            }
            synchronized (first) {
                sleep(20);
                synchronized (second) {
                    System.out.println("  " + Thread.currentThread().getName() + " transfer ok");
                }
            }
        };

        var ok1 = new Thread(transfer, "transfer-1");
        var ok2 = new Thread(transfer, "transfer-2");
        ok1.start(); ok2.start();
        ok1.join(); ok2.join();
        System.out.println("  both transfers completed without deadlock");
    }

    // --- Section 4: livelock ---
    static void livelock() throws InterruptedException {
        System.out.println("[Section 4] livelock");

        var lockA = new ReentrantLock();
        var lockB = new ReentrantLock();

        var attempts = new AtomicInteger();
        var done     = new AtomicBoolean();

        Runnable politeWorker = () -> {
            while (!done.get() && attempts.incrementAndGet() < 1000) {
                try {
                    if (lockA.tryLock()) {
                        try {
                            if (lockB.tryLock()) {
                                try { done.set(true); return; }
                                finally { lockB.unlock(); }
                            }
                        } finally { lockA.unlock(); }
                    }
                    // Both threads release and politely retry — symmetric back-off → livelock.
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); return;
                }
            }
        };

        var w1 = Thread.ofPlatform().name("polite-1").unstarted(politeWorker);
        var w2 = Thread.ofPlatform().name("polite-2").unstarted(politeWorker);
        w1.start(); w2.start();
        w1.join(500); w2.join(500);
        System.out.println("  attempts after 500 ms = " + attempts.get()
                + " (busy retrying — symmetric strategies lead to livelock)");
        // Cure: add randomised jitter or asymmetric retry windows.
        done.set(true);
        w1.interrupt(); w2.interrupt();
    }

    // --- Section 5: starvation ---
    static void starvation() throws InterruptedException {
        System.out.println("[Section 5] starvation");

        // Non-fair lock under contention. Many fast workers + 1 slow waiter:
        // the slow waiter can be barged past indefinitely.
        var unfair = new ReentrantLock(false);
        var counts = new java.util.concurrent.ConcurrentHashMap<String, Integer>();

        Runnable worker = () -> {
            for (int i = 0; i < 200; i++) {
                unfair.lock();
                try {
                    counts.merge(Thread.currentThread().getName(), 1, Integer::sum);
                } finally { unfair.unlock(); }
            }
        };
        Thread[] ts = new Thread[6];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(worker, "fast-" + i);
            ts[i].start();
        }
        for (var t : ts) t.join();
        System.out.println("  unfair distribution = " + counts);
        System.out.println("  cure: new ReentrantLock(true) — FIFO at the cost of throughput");
    }

    // --- Section 7: diagnostics workflow ---
    static void diagnosticsHints() {
        System.out.println("[Section 7] diagnostics workflow");
        long pid = ProcessHandle.current().pid();
        System.out.println("  PID            = " + pid);
        System.out.println("  thread dump    : jstack " + pid);
        System.out.println("  signal-based   : kill -3 " + pid);
        System.out.println("  GUI            : VisualVM, JConsole, JMC");
        System.out.println("  flight recorder: jcmd " + pid + " JFR.start duration=10s filename=rec.jfr");
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public static void main(String[] args) throws InterruptedException {
        deadlockDemo();
        lockOrderingFix();
        livelock();
        starvation();
        diagnosticsHints();
        // Note: the deadlock demo leaves T1/T2 stuck. Mark them daemon-ish via System.exit
        // so the JVM does not hang at the end of the file.
        System.out.println("Mod014ThreadingProblems finished");
        System.out.flush();
        // Force JVM exit to clean up the deadlocked threads from §2.
        Runtime.getRuntime().halt(0);
    }

    @SuppressWarnings("unused")
    private static void timedHelper() throws InterruptedException {
        // Reference: tryLock with timeout — preferred over plain lock() in deadlock-prone code.
        var lock = new ReentrantLock();
        if (lock.tryLock(50, TimeUnit.MILLISECONDS)) {
            try { /* ... */ } finally { lock.unlock(); }
        } else {
            // back off, log, alert
        }
    }
}
