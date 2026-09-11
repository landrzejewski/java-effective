package pl.training.concurrency.extras.tests;

/**
 * A JVM that deadlocks on purpose and then stays alive, so you can attach a tool to it and see what a deadlock
 * looks like from the outside (Mod014 §2 and §6).
 *
 * <p>Run it, then from another terminal:
 * <pre>
 *   jstack &lt;pid&gt;        # prints "Found one Java-level deadlock:" and the full cycle
 *   jcmd &lt;pid&gt; Thread.print
 * </pre>
 * or attach VisualVM / JConsole, whose Threads tab flags the deadlock automatically.
 *
 * <p>Note that nothing inside the JVM can break the cycle: {@code Thread.stop()} was removed in Java 26, and a
 * thread queued on a {@code synchronized} block ignores interrupts (Mod003 §7). Killing the process is the only
 * way out — which is exactly why lock ordering (Mod014 §3) matters.
 */
public class DeadlockDemo {

    private static final Object lock1 = new Object();
    private static final Object lock2 = new Object();

    public static void main(String[] args) throws InterruptedException {
        long pid = ProcessHandle.current().pid();
        System.out.println("Application started. PID: " + pid);
        System.out.println("Attach a tool and inspect the threads, e.g.  jstack " + pid);

        var first = new Thread(() -> {
            synchronized (lock1) {
                System.out.println("Thread 1: acquired lock1");
                sleep(100);
                System.out.println("Thread 1: trying to acquire lock2");
                synchronized (lock2) {
                    System.out.println("Thread 1: acquired lock2");   // unreachable
                }
            }
        }, "Worker-Thread-1");

        var second = new Thread(() -> {
            synchronized (lock2) {
                System.out.println("Thread 2: acquired lock2");
                sleep(100);
                System.out.println("Thread 2: trying to acquire lock1");
                synchronized (lock1) {
                    System.out.println("Thread 2: acquired lock1");   // unreachable
                }
            }
        }, "Worker-Thread-2");

        first.start();
        second.start();

        Thread.sleep(500);
        System.out.println("Thread 1 state: " + first.getState() + ", thread 2 state: " + second.getState());
        System.out.println("Both are BLOCKED forever. Press Ctrl-C to stop the JVM.");

        // Keep the process alive for the profiler. join() on either thread would do the same,
        // but this says out loud that the wait is intentional and unbounded.
        first.join();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
