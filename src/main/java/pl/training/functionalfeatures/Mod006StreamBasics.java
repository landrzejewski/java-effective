package pl.training.functionalfeatures;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.SequencedCollection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public final class Mod006StreamBasics {

    private Mod006StreamBasics() {}

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

    private static final Map<String, String> NICKNAMES =
            Map.of("alice", "Ally", "bob", "Bobby");

    /*
    Creating streams

    Seven common entry points:

    - collection.stream() - from any Collection. Most ubiquitous.
    - Stream.of(a, b, c, ...) - from a fixed varargs list.
    - Arrays.stream(array) - from an array; primitive overloads return IntStream/LongStream/DoubleStream.
    - Stream.iterate(seed, fn) - infinite stream by repeated function application. Bounded version
      Stream.iterate(seed, hasNext, next) (Java 9+) expresses a finite recurrence.
    - Stream.generate(supplier).limit(n) - infinite, then bounded.
    - Stream.builder() - imperative push-style construction; rarely needed.
    - Stream.ofNullable(x) (Java 9+) - a stream of one element, or empty if x is null. The clean way to feed a
      possibly-null value into a flatMap without an if.

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

        // (f) Stream.builder - imperative push
        var built = Stream.<String>builder().add("x").add("y").add("z").build().toList();
        System.out.println("  Stream.builder() = " + built);

        // (g) Stream.ofNullable - 0 or 1 elements. Compare the two lines below: without it you would
        //     need an if-null inside the flatMap lambda.
        var lookups = Stream.of("alice", "nobody", "bob")
                .flatMap(name -> Stream.ofNullable(NICKNAMES.get(name)))
                .toList();
        System.out.println("  Stream.ofNullable in flatMap = " + lookups);
    }

    /*
    Intermediate operations

    All intermediate ops return another Stream and are lazy - they remember what to do but do nothing until a
    terminal op pulls.

    - filter(predicate) - keep elements matching the predicate.
    - map(fn) - transform each element.
    - flatMap(fn) - transform each element into a stream, then flatten.
    - distinct() - remove duplicates by equals. Stateful - remembers what it has seen.
    - sorted([cmp]) - sort. Stateful and blocking - must consume the entire source before emitting.
    - peek(consumer) - debugging hook; the consumer runs as elements flow past.
    - limit(n) / skip(n) - short-circuiting and stateful, respectively.
    - takeWhile(p) / dropWhile(p) - stop / start emitting on first failure (Java 9+).
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

    Eager - they pull elements through the pipeline and produce a final result. After a terminal op the stream is
    consumed and cannot be reused.

    - forEach(consumer) - side-effecting iteration.
    - collect(collector) - accumulate into a collection or map (Mod008).
    - reduce(...) - fold into a single value.
    - count(), min(cmp), max(cmp) - aggregate.
    - findFirst(), findAny() - short-circuit; return Optional.
    - anyMatch(p), allMatch(p), noneMatch(p) - short-circuit predicates.
    - toList() (Java 16+) - concise replacement for collect(Collectors.toList()); result is unmodifiable.
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

        // reduce - fold totals manually (Mod008 introduces the Collector-based shortcut).
        double sum = ORDERS.stream().mapToDouble(Order::total).reduce(0.0, Double::sum);
        System.out.printf(Locale.ROOT, "  reduce sum    = %.2f%n", sum);
    }

    /*
    Lazy evaluation

    - An intermediate op alone does nothing. The pipeline only runs when a terminal op is invoked.
    - peek is the easiest way to see this: without a terminal op, the consumer passed to peek never fires.
    - Short-circuiting terminal ops (findFirst, anyMatch, limit upstream of any terminal) consume only as much of
      the source as needed. This makes pipelines on infinite streams useful - Stream.iterate(0, i -> i+1).filter(...)
      .findFirst() will return after the first match.

    One consequence surprises people: since Java 9, count() may skip the pipeline entirely. If the source knows
    its size and no upstream operation can change that size (map and peek cannot; filter and flatMap can), the
    library computes the count from the source and never traverses. This is a library optimisation in
    ReferencePipeline.count() driven by the SIZED stream flag - not something the JIT does.

    So list.stream().peek(sideEffect).count() runs no side effects at all, and .map(mayThrow).count() throws
    nothing. Never put work you depend on upstream of count(); use toList().size() if you need the traversal.
    */
    static void lazyEvaluation() {
        System.out.println("[Section 4] lazy evaluation");

        // peek without a terminal op - the consumer never runs.
        var pipeline = Stream.of(1, 2, 3)
                .peek(x -> System.out.println("    peek emitted " + x));
        System.out.println("  pipeline built; peek did not fire yet (no terminal op)");

        // Adding a *consuming* terminal op pulls elements through.
        // Note it must be a CONSUMING one: count() would not work here. See the note below.
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
            s.count();                                  // second consumption - throws
        } catch (IllegalStateException e) {
            System.out.println("  second use threw: " + e.getClass().getSimpleName());
        }

        // To reuse: create a fresh stream each time.
        long again = ORDERS.stream().count();
        System.out.println("  fresh stream count = " + again);
    }

    /*
    Encounter order

    A stream has an encounter order if its source defines one. That property decides what many operations are
    even allowed to mean:

    - Ordered sources: List, arrays, LinkedHashSet/LinkedHashMap, Stream.of, Stream.iterate.
    - Unordered sources: HashSet, HashMap.

    findFirst, limit, skip, takeWhile and toList() honour the encounter order; findAny and forEach explicitly do
    not promise to. On a sequential stream the difference is invisible - it becomes real the moment the pipeline
    goes parallel (see pl.training.concurrency.Mod010ParallelStreams). unordered() drops the guarantee on
    purpose, which lets the implementation take faster paths.

    Java 21 added SequencedCollection, which finally names the "has a first and a last" property in the type
    system: getFirst / getLast / addFirst / addLast / reversed. reversed() returns a view, so streaming it costs
    nothing extra and replaces the old "copy the list, Collections.reverse, stream it" dance.
    */
    static void encounterOrder() {
        System.out.println("[Section 6] encounter order and SequencedCollection");

        // An ordered source: findFirst is meaningful and repeatable.
        System.out.println("  List findFirst  = " + ORDERS.stream().map(Order::customer).findFirst().orElseThrow());

        // An unordered source: findFirst is legal but the "first" is whatever the hash layout gives.
        var hashed = new HashSet<>(List.of("alice", "bob", "carla"));
        System.out.println("  HashSet order   = " + hashed.stream().toList() + "  <- not the insertion order");

        // SequencedCollection (Java 21): first/last/reversed without copying.
        SequencedCollection<Order> seq = ORDERS;
        System.out.println("  getFirst        = " + seq.getFirst().sku());
        System.out.println("  getLast         = " + seq.getLast().sku());
        System.out.println("  reversed stream = " + seq.reversed().stream().map(Order::sku).toList());
    }

    /*
    Streams that must be closed

    Stream extends AutoCloseable. Collection-backed streams hold no resource and never need closing - which is
    why close() is invisible in most code. I/O-backed streams are the exception:

        Files.lines(path), Files.list(dir), Files.walk(dir), Files.find(...)

    each hold an open file handle for as long as the pipeline is alive. Not closing them leaks descriptors until
    the process runs out. Put them in a try-with-resources:

        try (var lines = Files.lines(path)) {
            return lines.filter(...).toList();          // materialise INSIDE the block
        }

    The other half of the rule: never return a lazy Stream out of the try block. The stream would be closed
    before the caller pulls the first element. Materialise (toList) inside, or hand the caller the resource.

    onClose(runnable) registers an extra cleanup action, which is how you would build such a stream yourself.
    */
    static void closingStreams() {
        System.out.println("[Section 7] closing streams");

        // Collection-backed: closing is a no-op, but it is still legal.
        try (var s = ORDERS.stream()) {
            System.out.println("  collection stream, no resource = " + s.count());
        }

        // Simulating an I/O-backed stream: onClose is where the file handle would be released.
        var closed = new boolean[1];
        try (var resourceBacked = Stream.of("line-1", "line-2").onClose(() -> closed[0] = true)) {
            System.out.println("  resource-backed lines = " + resourceBacked.toList());
        }
        System.out.println("  onClose ran on exit of try-with-resources = " + closed[0]);
    }

    public static void main(String[] args) {
        creatingStreams();
        intermediateOps();
        terminalOps();
        lazyEvaluation();
        oneShotStream();
        encounterOrder();
        closingStreams();
        System.out.println("Mod006StreamBasics finished");
    }
}
