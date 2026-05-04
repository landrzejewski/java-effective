package pl.training.concurrency;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

// =================================================================================================
// Section 1: Sequential vs parallel
// =================================================================================================

/*
## Sequential vs parallel

- A sequential stream processes elements one at a time on the calling thread.
- A parallel stream splits the data across multiple threads, processes the
chunks independently, and combines the partial results.
- Two ways to ask for parallelism: `collection.parallelStream()` or
`collection.stream().parallel()`. Both produce the same kind of stream.
- Going parallel is *not free* — there is split, schedule, and combine overhead.
The win comes only when the per-element work is large enough to outweigh that
overhead, and the data structure can be split efficiently.
*/

// =================================================================================================
// Section 2: What runs where
// =================================================================================================

/*
## What runs where

- Parallel streams run on `ForkJoinPool.commonPool()` by default. The same pool
backs `CompletableFuture.supplyAsync` (Mod009) and any custom fork-join workload
(Mod008).
- Default size is `availableProcessors() - 1`. The exact thread name pattern is
`ForkJoinPool.commonPool-worker-N`.
- Because the pool is shared, *long-running or blocking* parallel-stream stages
contend with everything else on the pool. For blocking I/O, do not use parallel
streams; use a dedicated executor and `CompletableFuture` (Mod009) or virtual
threads (Mod011).
*/

// =================================================================================================
// Section 3: When parallel pays off
// =================================================================================================

/*
## When parallel pays off

The break-even point depends on N × per-element-cost:

- **Trivial per-element work** (a single arithmetic op): parallel is almost
always slower because the constant overhead per split dominates.
- **Heavy per-element work** (parsing, hashing, decryption): parallel scales
near-linearly with cores up to the parallelism limit.
- **Variable per-element work**: work-stealing inside the fork-join pool
absorbs imbalance, so parallel can still win even if elements have wildly
different costs.

Always measure on the actual workload — the tipping point depends on JIT, cache
behavior, GC, and the source's spliterator quality.
*/

// =================================================================================================
// Section 4: Order-sensitive operations
// =================================================================================================

/*
## Order-sensitive operations

- Parallel streams preserve the *encounter order* of the source unless you
explicitly opt out (`.unordered()`).
- `findFirst()` must search left-to-right and is more expensive on parallel
streams than `findAny()`. Pick `findAny` when any matching element will do.
- `forEach` does not guarantee order, even on a sequential stream's parallel
fork. Use `forEachOrdered` if you need source order.
- `limit` and `skip` on parallel ordered streams are also expensive — they need
to wait for elements to be processed in the right sequence to know which N you
keep.
*/

// =================================================================================================
// Section 5: Stateful lambdas pitfall
// =================================================================================================

/*
## Stateful lambdas pitfall

- Lambdas passed to stream operations should be *stateless*: they should not
read or write shared mutable state.
- A stateful lambda inside `forEach` on a parallel stream produces lost updates
(if the structure is not thread-safe), `ConcurrentModificationException`
(fail-fast collections), or arbitrary results (HashMap rehash mid-write).
- The fix is usually to express the same logic with `collect`, which the runtime
parallelises correctly.
*/

// =================================================================================================
// Section 6: Reduce associativity
// =================================================================================================

/*
## Reduce associativity

- `reduce(identity, accumulator, combiner)` requires the combine function to be
**associative** (`(a op b) op c == a op (b op c)`). Otherwise the parallel
result depends on how the input was split — non-deterministic and almost always
wrong.
- The two-argument `reduce(identity, fn)` requires identity + associativity but
no combiner — the JVM uses `fn` for both. Same constraint.
- `reduce` should also be *non-interfering* — it must not modify the source.
- For string concatenation, do not use `reduce("", String::concat)` (O(n²)
allocations and not associative for the empty-string identity in subtle ways).
Use `Collectors.joining` instead.
*/

// =================================================================================================
// Section 7: collect and concurrent collectors
// =================================================================================================

/*
## collect and concurrent collectors

- `collect(Collectors.toList())` on a parallel stream still gives correct
results, but the collector merges per-thread accumulators sequentially, which
limits speedup.
- A *concurrent* collector (`toConcurrentMap`, `groupingByConcurrent`) writes
into a single shared concurrent container, skipping the merge step. It must
also be marked `UNORDERED` (or used on an unordered stream) so the runtime is
free to write into the container in any order.
- For grouping reductions on parallel streams, prefer `groupingByConcurrent` —
it tends to be measurably faster on multi-core machines.
*/

// =================================================================================================
// Section 8: Custom pool for parallel streams
// =================================================================================================

/*
## Custom pool for parallel streams

- `Stream.parallel()` does not let you pass an executor — the JVM hardcodes the
common pool.
- The standard workaround is to wrap the entire stream pipeline inside
`ForkJoinPool.submit(() -> stream.parallel()...).get()`. That submission runs
on the custom pool, and ForkJoin tasks created from inside (which is what the
spliterator does) inherit that pool.
- Use this when:
  - The stream might block briefly (you do not want to block the common pool).
  - You want a different parallelism than the default.
  - You want the work isolated for monitoring.
*/

public final class Mod010ParallelStreams {

    private Mod010ParallelStreams() {}

    private static final int LOG_COUNT = 5_000_000;

    record LogEntry(String level, String service, long latencyMs) {}

    private static List<LogEntry> generateLogs(int n) {
        var rnd = new java.util.Random(7);
        String[] levels = { "INFO", "WARN", "ERROR" };
        String[] services = { "auth", "billing", "search", "feed", "notify" };
        var out = new ArrayList<LogEntry>(n);
        for (int i = 0; i < n; i++) {
            out.add(new LogEntry(
                    levels[rnd.nextInt(levels.length)],
                    services[rnd.nextInt(services.length)],
                    rnd.nextInt(500)));
        }
        return out;
    }

    // --- Section 1: sequential vs parallel ---
    static void sequentialVsParallel(List<LogEntry> logs) {
        System.out.println("[Section 1] sequential vs parallel");

        long t0 = System.nanoTime();
        long seq = logs.stream().mapToLong(LogEntry::latencyMs).sum();
        long seqMs = (System.nanoTime() - t0) / 1_000_000;

        long t1 = System.nanoTime();
        long par = logs.parallelStream().mapToLong(LogEntry::latencyMs).sum();
        long parMs = (System.nanoTime() - t1) / 1_000_000;

        System.out.println("  sequential = " + seq + " in " + seqMs + " ms");
        System.out.println("  parallel   = " + par + " in " + parMs + " ms");
    }

    // --- Section 2: what runs where ---
    static void whatRunsWhere(List<LogEntry> logs) {
        System.out.println("[Section 2] what runs where");

        var threadNames = new ConcurrentSkipListSet<String>();
        long total = logs.parallelStream()
                .peek(e -> threadNames.add(Thread.currentThread().getName()))
                .mapToLong(LogEntry::latencyMs)
                .sum();
        System.out.println("  total = " + total);
        System.out.println("  parallelism = " + ForkJoinPool.commonPool().getParallelism());
        System.out.println("  workers observed = " + threadNames.size() + " " + threadNames);
    }

    // --- Section 3: when parallel does NOT pay off (trivial work) ---
    static void parallelDoesNotAlwaysWin() {
        System.out.println("[Section 3] when parallel does not win");

        var nums = IntStream.rangeClosed(1, 1_000_000).boxed().toList();

        long t0 = System.nanoTime();
        long seq = nums.stream().mapToLong(Integer::longValue).sum();
        long seqMs = (System.nanoTime() - t0) / 1_000_000;

        long t1 = System.nanoTime();
        long par = nums.parallelStream().mapToLong(Integer::longValue).sum();
        long parMs = (System.nanoTime() - t1) / 1_000_000;

        System.out.println("  trivial sum: sequential = " + seqMs + " ms, parallel = " + parMs + " ms");
        System.out.println("  → trivial per-element cost: parallel overhead dominates");
    }

    // --- Section 4: order-sensitive operations ---
    static void orderSensitiveOps() {
        System.out.println("[Section 4] order-sensitive operations");

        var range = IntStream.rangeClosed(1, 1_000_000).boxed().toList();
        // findFirst respects encounter order — the answer is deterministic.
        int first = range.parallelStream().filter(x -> x > 100).findFirst().orElseThrow();
        // findAny is allowed to return any matching element.
        int any   = range.parallelStream().filter(x -> x > 100).findAny().orElseThrow();
        System.out.println("  findFirst = " + first + ", findAny = " + any);

        // forEach on a parallel stream: order is not preserved.
        System.out.print("  forEach (parallel, unordered): ");
        Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).parallel().forEach(n -> System.out.print(n + " "));
        System.out.println();

        // forEachOrdered: source order restored, at a cost.
        System.out.print("  forEachOrdered (parallel):     ");
        Stream.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).parallel().forEachOrdered(n -> System.out.print(n + " "));
        System.out.println();
    }

    // --- Section 5: stateful lambda pitfall ---
    static void statefulLambdaPitfall() {
        System.out.println("[Section 5] stateful lambda pitfall");

        // BAD: writing into a non-thread-safe ArrayList from a parallel stream.
        var bag = new ArrayList<Integer>();
        try {
            IntStream.range(0, 100_000).parallel().forEach(bag::add);
            System.out.println("  buggy size = " + bag.size() + " (expected 100000) — usually wrong");
        } catch (Exception e) {
            System.out.println("  buggy stream threw " + e.getClass().getSimpleName());
        }

        // GOOD: let collect() do the parallel-safe accumulation.
        var safe = IntStream.range(0, 100_000).parallel().boxed().toList();
        System.out.println("  collect-based size = " + safe.size());
    }

    // --- Section 6: reduce associativity ---
    static void reduceAssociativity() {
        System.out.println("[Section 6] reduce associativity");

        // Demo: an INCONSISTENT combiner (sum within a chunk, but product across chunks).
        // This violates the contract of reduce(identity, accumulator, combiner) and makes
        // the parallel result depend on how the input was split — non-deterministic.
        var nums = List.of(1, 2, 3, 4, 5, 6, 7, 8);
        int seq = nums.stream().reduce(0, Integer::sum, (a, b) -> a * b);   // 36 (combiner not used)
        int par = nums.parallelStream().reduce(0, Integer::sum, (a, b) -> a * b);
        System.out.println("  seq inconsistent-reduce: " + seq);
        System.out.println("  par inconsistent-reduce: " + par
                + "  ← combiner is not an associative extension of accumulator");

        // Use Collectors.joining for strings — it is associative under the hood.
        var ok = Stream.of("a", "b", "c", "d", "e", "f", "g", "h").parallel()
                .collect(Collectors.joining("-"));
        System.out.println("  joining (correct):       " + ok);
    }

    // --- Section 7: concurrent collectors ---
    static void concurrentCollectors(List<LogEntry> logs) {
        System.out.println("[Section 7] concurrent collectors");

        long t1 = System.nanoTime();
        Map<String, Long> grouped = logs.parallelStream()
                .collect(Collectors.groupingBy(LogEntry::service, Collectors.counting()));
        long groupingMs = (System.nanoTime() - t1) / 1_000_000;

        long t2 = System.nanoTime();
        Map<String, Long> groupedConcurrent = logs.parallelStream()
                .collect(Collectors.groupingByConcurrent(LogEntry::service, Collectors.counting()));
        long concurrentMs = (System.nanoTime() - t2) / 1_000_000;

        System.out.println("  groupingBy            -> " + grouped + " in " + groupingMs + " ms");
        System.out.println("  groupingByConcurrent  -> " + groupedConcurrent + " in " + concurrentMs + " ms");
    }

    // --- Section 8: custom pool ---
    static void customPool(List<LogEntry> logs) throws InterruptedException, ExecutionException {
        System.out.println("[Section 8] custom pool");

        try (var pool = new ForkJoinPool(4)) {
            var threadNames = new ConcurrentSkipListSet<String>();
            long total = pool.submit(() -> logs.parallelStream()
                    .peek(e -> threadNames.add(Thread.currentThread().getName()))
                    .mapToLong(LogEntry::latencyMs)
                    .sum()).get();
            System.out.println("  total = " + total);
            System.out.println("  workers observed = " + threadNames.size() + " (custom pool, NOT common)");
            System.out.println("  one of them: " + threadNames.iterator().next());
        }
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        var logs = generateLogs(LOG_COUNT);

        sequentialVsParallel(logs);
        whatRunsWhere(logs);
        parallelDoesNotAlwaysWin();
        orderSensitiveOps();
        statefulLambdaPitfall();
        reduceAssociativity();
        concurrentCollectors(logs);
        customPool(logs);
        System.out.println("Mod010ParallelStreams finished");
    }
}
