package pl.training.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.atomic.AtomicInteger;

/*
Mental model

The shape of a structured fan-out:

parent task                      open scope ─┐
  │                                          │
  ├─ fork sub-task A (virtual thread)        │ all children's lifetimes
  ├─ fork sub-task B (virtual thread)        │ are bounded by the scope
  ├─ fork sub-task C (virtual thread)        │
  │                                          │
  └─ scope.join()  ←──── waits for all ──────┘
       (or until the joiner short-circuits)

Closing the scope (try-with-resources) cancels any still-running children. If the parent itself is cancelled
(because its parent scope cancelled it), the cancellation propagates downwards. The whole tree is structurally
enclosed — there are no orphans.
*/

final class BestEffortJoiner<T> implements Joiner<T, List<T>> {
    private final ConcurrentLinkedQueue<T> ok = new ConcurrentLinkedQueue<>();
    @Override public boolean onComplete(Subtask<? extends T> subtask) {
        if (subtask.state() == Subtask.State.SUCCESS) ok.add(subtask.get());
        return false; // never short-circuit
    }
    @Override public List<T> result() { return new ArrayList<>(ok); }
}

public final class Mod012StructuredConcurrency {

    private Mod012StructuredConcurrency() {}

    // Simulated downstream services for the dashboard scenario.
    private static String fetchProfile()         { sleep(80);  return "alice"; }
    private static List<String> fetchOrders()    { sleep(120); return List.of("o-1", "o-2"); }
    private static List<String> fetchRecos()     { sleep(70);  return List.of("r-1", "r-2", "r-3"); }
    private static String fetchProfileFailing()  { sleep(40);  throw new RuntimeException("profile down"); }

    /*
    The unstructured-concurrency problem

    - A CompletableFuture chain that fans out to N services has no enclosing "task" object. Each branch lives
      independently:
      - If one branch throws, the others keep running and continue to consume threads, sockets, DB rows.
      - Cancelling one future does not propagate; siblings cannot be told to stop.
      - Stack traces of inner stages do not point back to the call site that submitted them — they show the executor
        worker, period.
    - This pattern leaks resources and makes failure handling fragile. Structured concurrency (JEP 505, fifth preview
      in Java 25) attaches a parent–child relationship between a coordinating task and its sub-tasks, so the lifetime
      of the children is bounded by the lifetime of the parent.
    */
    static void unstructuredProblem() {
        System.out.println("[Section 1] unstructured-concurrency problem");

        var ran = new AtomicInteger();
        var siblings = List.of(80L, 200L, 60L).stream().map(delay ->
                CompletableFuture.runAsync(() -> { sleep(delay); ran.incrementAndGet(); })
        ).toList();

        // Cancel one — the others keep going. We wait for them with a sleep to count.
        siblings.get(0).cancel(true);
        sleep(250);
        System.out.println("  with CompletableFuture, siblings that still ran = " + ran.get()
                + " (cancellation did not propagate)");
    }

    /*
    StructuredTaskScope.open() — basic shape

    try (var scope = StructuredTaskScope.open()) {
        var a = scope.fork(() -> fetchProfile());
        var b = scope.fork(() -> fetchOrders());
        scope.join();                   // waits for ALL forks to finish
        var profile = a.get();
        var orders  = b.get();
    }

    - fork(Callable) returns a Subtask<T>. Reading subtask.get() before scope.join() returns is illegal.
    - scope.join() blocks until every forked subtask has finished or the joiner short-circuits (see §4).
    - The try-with-resources close() makes sure that even if join() is skipped (e.g., an exception jumps over it),
      every still-running subtask is cancelled before the try block exits.
    - Each fork creates a virtual thread by default — see §9.
    */
    static void basicScope() throws InterruptedException {
        System.out.println("[Section 3] StructuredTaskScope.open() — dashboard fan-out");

        long t0 = System.nanoTime();
        try (var scope = StructuredTaskScope.open()) {
            Subtask<String> profile = scope.fork(Mod012StructuredConcurrency::fetchProfile);
            Subtask<List<String>> orders = scope.fork(Mod012StructuredConcurrency::fetchOrders);
            Subtask<List<String>> recos  = scope.fork(Mod012StructuredConcurrency::fetchRecos);

            scope.join();

            System.out.println("  profile=" + profile.get()
                    + ", orders=" + orders.get()
                    + ", recommendations=" + recos.get());
        }
        System.out.println("  wall time = " + ((System.nanoTime() - t0) / 1_000_000) + " ms");
    }

    /*
    Joiner.allSuccessfulOrThrow — invariant fan-out

    - All sub-tasks must succeed; the first failure cancels the rest.
    - scope.join() returns a Stream<Subtask<T>> of the successful sub-tasks in completion order.
    - This is the dashboard / aggregation pattern: load all the pieces; if any piece is broken, fail the entire
      request and free the in-flight resources.
    */
    static void allSuccessfulOrThrow() throws InterruptedException {
        System.out.println("[Section 4] Joiner.allSuccessfulOrThrow");

        long t0 = System.nanoTime();
        try (var scope = StructuredTaskScope.open(Joiner.<Object>allSuccessfulOrThrow())) {
            scope.fork(Mod012StructuredConcurrency::fetchProfileFailing); // fails at ~40 ms
            scope.fork(() -> {                                            // would take 500 ms
                try { Thread.sleep(500); }
                catch (InterruptedException e) {
                    System.out.println("  long subtask was interrupted (cancellation propagated)");
                    throw e;
                }
                return List.<String>of();
            });
            scope.join();
            throw new IllegalStateException("should not reach here");
        } catch (Exception e) {
            System.out.println("  scope.join() rethrew: " + e.getClass().getSimpleName()
                    + " (cause = " + e.getCause() + ")");
        }
        System.out.println("  wall time = " + ((System.nanoTime() - t0) / 1_000_000)
                + " ms (≈ first-failure time, NOT max sibling time)");
    }

    /*
    Joiner.anySuccessfulResultOrThrow — race / first wins

    - Several sub-tasks compute the same answer in parallel; the first to return wins, the others are cancelled
      immediately.
    - scope.join() returns the value directly (typed T).
    - If every sub-task fails, join() throws with the last exception as the cause.
    - Use case: redundant providers (two DNS resolvers, several mirror caches), adaptive timeouts, or fastest-replica
      reads.
    */
    static void anySuccessfulRace() throws InterruptedException {
        System.out.println("[Section 5] Joiner.anySuccessfulResultOrThrow");

        try (var scope = StructuredTaskScope.open(Joiner.<String>anySuccessfulResultOrThrow())) {
            scope.fork(() -> { Thread.sleep(120); return "primary"; });
            scope.fork(() -> { Thread.sleep(40);  return "mirror-1"; });
            scope.fork(() -> { Thread.sleep(80);  return "mirror-2"; });

            String winner = scope.join();
            System.out.println("  winner = " + winner + " (others were cancelled mid-flight)");
        }
    }

    /*
    Joiner.allUntil(predicate) — partial fan-out

    - Wait for all sub-tasks until a user predicate returns true. A common case: "send 10 mirror queries, stop and
      return as soon as 3 of them return".
    - join() returns a Stream<Subtask<T>> of sub-tasks in the order they completed; calls subtask.get() (or state())
      safely.
    - The predicate is called every time a sub-task finishes; the running siblings are cancelled when it first
      returns true.
    */
    static void allUntilPredicate() throws InterruptedException {
        System.out.println("[Section 6] Joiner.allUntil(predicate)");

        var threshold = 3;
        var done = new AtomicInteger();
        try (var scope = StructuredTaskScope.open(Joiner.<Integer>allUntil(s -> {
            if (s.state() == Subtask.State.SUCCESS) done.incrementAndGet();
            return done.get() >= threshold; // stop once we have N successes
        }))) {
            for (int i = 0; i < 10; i++) {
                final int delay = 30 + i * 30;
                scope.fork(() -> { Thread.sleep(delay); return delay; });
            }
            var subtasks = scope.join();
            var successful = subtasks
                    .filter(s -> s.state() == Subtask.State.SUCCESS)
                    .map(Subtask::get)
                    .toList();
            System.out.println("  first " + threshold + " successful results = " + successful);
        }
    }

    /*
    Custom joiner

    - Joiners are an open SPI: implement Joiner<T, R>:
      - onComplete(Subtask<? extends T>) — called when a sub-task ends. Return true to short-circuit (cancel the
        scope), false to keep collecting.
      - result() — produces the final aggregated value returned by scope.join().
    - The example below is BestEffortJoiner<T>: it collects every successful sub-task and silently ignores failures.
      The shape mirrors how a Kafka-style "fan-out, accept what we got" stage is built.
    */
    static void customBestEffortJoiner() throws InterruptedException {
        System.out.println("[Section 7] custom BestEffortJoiner");

        try (var scope = StructuredTaskScope.open(new BestEffortJoiner<String>())) {
            scope.fork(() -> { Thread.sleep(20); return "A"; });
            scope.fork(() -> { throw new RuntimeException("flaky"); });
            scope.fork(() -> { Thread.sleep(50); return "C"; });
            List<String> results = scope.join();
            System.out.println("  successful results only: " + results);
        }
    }

    /*
    Cancellation propagation

    - When the joiner short-circuits (e.g., one branch threw under allSuccessfulOrThrow), the scope sends an
      interrupt to every still-running sub-task.
    - A sub-task that is currently in Thread.sleep, Object.wait, a BlockingQueue operation, or any other JDK blocking
      call is woken up with InterruptedException. A pure CPU-bound sub-task is responsible for checking
      Thread.currentThread().isInterrupted().
    - This is the single biggest qualitative win over CompletableFuture: failure in one branch immediately stops
      every other branch, so the request returns quickly and stops paying for work whose result will be thrown away.
    */
    static void cancellationPropagation() throws InterruptedException {
        System.out.println("[Section 8] cancellation propagation");

        long t0 = System.nanoTime();
        try (var scope = StructuredTaskScope.open(Joiner.<String>allSuccessfulOrThrow())) {
            scope.fork(() -> { throw new RuntimeException("immediate"); });
            scope.fork(() -> {
                try {
                    Thread.sleep(10_000); // would block 10 seconds
                    return "long";
                } catch (InterruptedException e) {
                    System.out.println("  long subtask received interrupt at "
                            + ((System.nanoTime() - t0) / 1_000_000) + " ms");
                    throw e;
                }
            });
            scope.join();
        } catch (Exception ignored) {}
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("  total wall time = " + ms + " ms (NOT 10 000 ms)");
    }

    /*
    Composition with virtual threads

    - scope.fork(Callable) creates a virtual thread for the sub-task by default — the same kind covered in
      Mod011VirtualThreads.
    - That makes structured concurrency the natural API for a server that wants to fan out N parallel calls per
      request: each call gets its own (cheap) virtual thread, the scope manages their lifetime, and back-pressure is
      implicit.
    - A StructuredTaskScope can be customised to use platform threads via
      StructuredTaskScope.open(Joiner, Configuration -> ...), but the default is fine for almost every workload.
    */
    static void virtualThreadComposition() throws InterruptedException {
        System.out.println("[Section 9] virtual-thread composition");

        var virtualCount = new AtomicInteger();
        try (var scope = StructuredTaskScope.open(Joiner.<Integer>allSuccessfulOrThrow())) {
            for (int i = 0; i < 1000; i++) {
                final int id = i;
                scope.fork(() -> {
                    if (Thread.currentThread().isVirtual()) virtualCount.incrementAndGet();
                    Thread.sleep(20);
                    return id;
                });
            }
            scope.join();
        }
        System.out.println("  virtual-thread sub-tasks = " + virtualCount.get() + " / 1000");
    }

    /*
    Comparison with CompletableFuture

      Aspect                       CompletableFuture               StructuredTaskScope
      ---------------------------- ------------------------------- ----------------------------
      Fan-out / fan-in             allOf + join() per branch       fork + join()
      Cancellation propagation     NO (Mod009 §9)                  YES — sibling-aware
      Exception aggregation        manual                          automatic via joiner
      Stack traces                 severed at executor boundary    preserved (synchronous shape)
      Best for                     event-driven, callbacks         request-bounded fan-out
    */
    static void comparisonHeadToHead() throws InterruptedException {
        System.out.println("[Section 10] CompletableFuture vs StructuredTaskScope");

        // A: CompletableFuture-based dashboard.
        long t1 = System.nanoTime();
        var profileF = CompletableFuture.supplyAsync(Mod012StructuredConcurrency::fetchProfile);
        var ordersF  = CompletableFuture.supplyAsync(Mod012StructuredConcurrency::fetchOrders);
        var recosF   = CompletableFuture.supplyAsync(Mod012StructuredConcurrency::fetchRecos);
        var dashboardA = CompletableFuture.allOf(profileF, ordersF, recosF)
                .thenApply(v -> "user=" + profileF.join()
                        + ", orders=" + ordersF.join()
                        + ", recos=" + recosF.join())
                .join();
        long aMs = (System.nanoTime() - t1) / 1_000_000;

        // B: Same dashboard with StructuredTaskScope.
        long t2 = System.nanoTime();
        String dashboardB;
        try (var scope = StructuredTaskScope.open()) {
            var p = scope.fork(Mod012StructuredConcurrency::fetchProfile);
            var o = scope.fork(Mod012StructuredConcurrency::fetchOrders);
            var r = scope.fork(Mod012StructuredConcurrency::fetchRecos);
            scope.join();
            dashboardB = "user=" + p.get() + ", orders=" + o.get() + ", recos=" + r.get();
        }
        long bMs = (System.nanoTime() - t2) / 1_000_000;

        System.out.println("  CompletableFuture       (" + aMs + " ms): " + dashboardA);
        System.out.println("  StructuredTaskScope     (" + bMs + " ms): " + dashboardB);
        System.out.println("  → both equivalent on the happy path; STS adds cancellation correctness");
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public static void main(String[] args) throws InterruptedException {
        unstructuredProblem();
        basicScope();
        allSuccessfulOrThrow();
        anySuccessfulRace();
        allUntilPredicate();
        customBestEffortJoiner();
        cancellationPropagation();
        virtualThreadComposition();
        comparisonHeadToHead();
        System.out.println("Mod012StructuredConcurrency finished");
    }
}
