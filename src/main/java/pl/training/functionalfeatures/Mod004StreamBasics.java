package pl.training.functionalfeatures;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public final class Mod004StreamBasics {

    private Mod004StreamBasics() {}

    record Order(String customer, String sku, int quantity, double unitPrice) {
        double total() { return quantity * unitPrice; }
    }

    private static final List<Order> ORDERS = List.of(
            new Order("alice", "SKU-001",  2,  19.99),
            new Order("bob",   "SKU-002",  1, 199.00),
            new Order("alice", "SKU-003", 10,   2.50),
            new Order("carla", "SKU-001",  5,  19.99),
            new Order("bob",   "SKU-004",  3,  49.50),
            new Order("alice", "SKU-002",  1, 199.00));

    /*
    Creating streams

    Six common entry points:

    - collection.stream() — from any Collection. Most ubiquitous.
    - Stream.of(a, b, c, ...) — from a fixed varargs list.
    - Arrays.stream(array) — from an array; primitive overloads return IntStream/LongStream/DoubleStream.
    - Stream.iterate(seed, fn) — infinite stream by repeated function application. Bounded version
      Stream.iterate(seed, hasNext, next) (Java 9+) expresses a finite recurrence.
    - Stream.generate(supplier).limit(n) — infinite, then bounded.
    - Stream.builder() — imperative push-style construction; rarely needed.

    Streams are not containers. They have no storage; they pull from a source and push values through a pipeline.
    */
    static void creatingStreams() {
        System.out.println("[Section 1] creating streams");

        // (a) from a collection
        var fromCol = ORDERS.stream().count();
        System.out.println("  collection.stream() count = " + fromCol);

        // (b) Stream.of
        var ofTotal = Stream.of("a", "b", "c").count();
        System.out.println("  Stream.of(...) count = " + ofTotal);

        // (c) Arrays.stream
        int[] nums = {1, 2, 3, 4};
        int sum = Arrays.stream(nums).sum();
        System.out.println("  Arrays.stream(int[]) sum = " + sum);

        // (d) Stream.iterate (bounded form, Java 9+)
        var firstTen = Stream.iterate(1, i -> i <= 10, i -> i + 1).toList();
        System.out.println("  Stream.iterate(1, <=10, +1) = " + firstTen);

        // (e) Stream.generate(supplier).limit
        var counter = new AtomicLong();
        var ids = Stream.generate(counter::incrementAndGet).limit(5).toList();
        System.out.println("  Stream.generate(...).limit(5) = " + ids);

        // (f) Stream.builder — imperative push
        var built = Stream.<String>builder().add("x").add("y").add("z").build().toList();
        System.out.println("  Stream.builder() = " + built);
    }

    /*
    Intermediate operations

    All intermediate ops return another Stream and are lazy — they remember what to do but do nothing until a
    terminal op pulls.

    - filter(predicate) — keep elements matching the predicate.
    - map(fn) — transform each element.
    - flatMap(fn) — transform each element into a stream, then flatten.
    - distinct() — remove duplicates by equals. Stateful — remembers what it has seen.
    - sorted([cmp]) — sort. Stateful and blocking — must consume the entire source before emitting.
    - peek(consumer) — debugging hook; the consumer runs as elements flow past.
    - limit(n) / skip(n) — short-circuiting and stateful, respectively.
    - takeWhile(p) / dropWhile(p) — stop / start emitting on first failure (Java 9+).
    */
    static void intermediateOps() {
        System.out.println("[Section 2] intermediate operations");

        var customers = ORDERS.stream()
                .filter(o -> o.total() >= 50)        // filter
                .map(Order::customer)                // map
                .distinct()                          // distinct (stateful)
                .sorted()                            // sorted (stateful, blocking)
                .toList();
        System.out.println("  customers with big orders = " + customers);

        // flatMap: split each customer's name into characters across all orders
        var charSet = ORDERS.stream()
                .map(Order::customer)
                .distinct()
                .flatMap(name -> name.chars().mapToObj(c -> (char) c))
                .distinct()
                .sorted()
                .toList();
        System.out.println("  unique letters across customer names = " + charSet);

        // takeWhile / dropWhile
        var take = Stream.of(1, 2, 3, 4, 5, 1, 2).takeWhile(n -> n < 4).toList();
        var drop = Stream.of(1, 2, 3, 4, 5, 1, 2).dropWhile(n -> n < 4).toList();
        System.out.println("  takeWhile(<4) = " + take);
        System.out.println("  dropWhile(<4) = " + drop);
    }

    /*
    Terminal operations

    Eager — they pull elements through the pipeline and produce a final result. After a terminal op the stream is
    consumed and cannot be reused.

    - forEach(consumer) — side-effecting iteration.
    - collect(collector) — accumulate into a collection or map (Mod005).
    - reduce(...) — fold into a single value.
    - count(), min(cmp), max(cmp) — aggregate.
    - findFirst(), findAny() — short-circuit; return Optional.
    - anyMatch(p), allMatch(p), noneMatch(p) — short-circuit predicates.
    - toList() (Java 16+) — concise replacement for collect(Collectors.toList()); result is unmodifiable.
    */
    static void terminalOps() {
        System.out.println("[Section 3] terminal operations");

        long count = ORDERS.stream().filter(o -> o.customer().equals("alice")).count();
        System.out.println("  count(alice) = " + count);

        double maxTotal = ORDERS.stream().mapToDouble(Order::total).max().orElse(0);
        System.out.println("  max total    = " + maxTotal);

        var biggest = ORDERS.stream().max(Comparator.comparingDouble(Order::total)).orElseThrow();
        System.out.println("  biggest order = " + biggest);

        boolean anyExpensive = ORDERS.stream().anyMatch(o -> o.total() > 500);
        System.out.println("  anyMatch(>500) = " + anyExpensive);

        // reduce — fold totals manually (Mod005 introduces the Collector-based shortcut).
        double sum = ORDERS.stream().mapToDouble(Order::total).reduce(0.0, Double::sum);
        System.out.printf("  reduce sum    = %.2f%n", sum);
    }

    /*
    Lazy evaluation

    - An intermediate op alone does nothing. The pipeline only runs when a terminal op is invoked.
    - peek is the easiest way to see this: without a terminal op, the consumer passed to peek never fires.
    - Short-circuiting terminal ops (findFirst, anyMatch, limit upstream of any terminal) consume only as much of
      the source as needed. This makes pipelines on infinite streams useful — Stream.iterate(0, i -> i+1).filter(...)
      .findFirst() will return after the first match.
    */
    static void lazyEvaluation() {
        System.out.println("[Section 4] lazy evaluation");

        // peek without a terminal op — the consumer never runs.
        var pipeline = Stream.of(1, 2, 3)
                .peek(x -> System.out.println("    peek emitted " + x));
        System.out.println("  pipeline built; peek did not fire yet (no terminal op)");

        // Adding a *consuming* terminal op pulls elements through.
        // (count() on a sized source is JIT-optimised to skip the pipeline; toList is not.)
        var collected = pipeline.toList();
        System.out.println("  after toList() the peek lines fired (above); collected = " + collected);

        // limit short-circuits an infinite stream.
        var firstFiveSquares = Stream.iterate(0, i -> i + 1)
                .map(i -> i * i)
                .limit(5)
                .toList();
        System.out.println("  first 5 squares = " + firstFiveSquares);
    }

    /*
    One-shot streams

    A Stream can be consumed exactly once. Reaching for the same stream variable after a terminal op throws
    IllegalStateException: stream has already been operated upon or closed.

    To "reuse", create a fresh stream from the source each time. If the source is expensive to traverse repeatedly,
    materialise once with .toList() and stream from the list.
    */
    static void oneShotStream() {
        System.out.println("[Section 5] one-shot stream");

        var s = ORDERS.stream();
        long first = s.count();
        System.out.println("  first count = " + first);
        try {
            s.count();                                  // second consumption — throws
        } catch (IllegalStateException e) {
            System.out.println("  second use threw: " + e.getClass().getSimpleName());
        }

        // To reuse: create a fresh stream each time.
        long again = ORDERS.stream().count();
        System.out.println("  fresh stream count = " + again);
    }

    public static void main(String[] args) {
        creatingStreams();
        intermediateOps();
        terminalOps();
        lazyEvaluation();
        oneShotStream();
        System.out.println("Mod004StreamBasics finished");
    }
}
