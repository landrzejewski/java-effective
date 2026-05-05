package pl.training.functionalfeatures;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class Mod005Collectors {

    private Mod005Collectors() {}

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
    Basic collectors

    stream.collect(Collectors.X(...)) is the canonical way to materialise a stream into a container. The most common
    building blocks:

    - toList() — modifiable ArrayList. Java 16+: Stream.toList() is the unmodifiable equivalent and is shorter to
      write.
    - toUnmodifiableList(), toUnmodifiableSet(), toUnmodifiableMap() — return immutable containers; mutating them
      throws.
    - toSet() — modifiable HashSet; order undefined.
    - toMap(keyFn, valueFn) — fail on duplicate keys (IllegalStateException). Use the 3-arg overload with a merge
      function if duplicates are possible.
    - joining([delimiter[, prefix, suffix]]) — concatenate CharSequence elements.
    */
    static void basicCollectors() {
        System.out.println("[Section 1] basic collectors");

        // toList vs toUnmodifiableList
        List<String> mutable = ORDERS.stream().map(Order::customer).distinct()
                .collect(Collectors.toList());        // mutable ArrayList
        List<String> immutable = ORDERS.stream().map(Order::customer).distinct()
                .toList();                            // unmodifiable
        System.out.println("  mutable customers   = " + mutable);
        System.out.println("  immutable customers = " + immutable);

        // toMap with a merge function — duplicates are summed.
        Map<String, Double> totalsBySku = ORDERS.stream().collect(
                Collectors.toMap(Order::sku, Order::total, Double::sum));
        System.out.println("  totals by SKU       = " + totalsBySku);

        // joining
        String list = ORDERS.stream().map(Order::sku).distinct()
                .collect(Collectors.joining(", ", "[", "]"));
        System.out.println("  joined SKUs         = " + list);
    }

    /*
    groupingBy

    Collectors.groupingBy(keyFn) groups elements by the result of keyFn, producing Map<K, List<T>> by default.

    A 2-arg overload groupingBy(keyFn, downstreamCollector) lets you replace the default toList() with anything:
    counting(), summingInt(), mapping(), reducing(), even another groupingBy() for nested groupings.

    A 3-arg overload groupingBy(keyFn, mapFactory, downstream) lets you choose the map type — TreeMap,
    LinkedHashMap, EnumMap — when ordering matters.
    */
    static void groupingBy() {
        System.out.println("[Section 2] groupingBy");

        // default downstream = toList
        Map<String, List<Order>> byCustomer = ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer));
        byCustomer.forEach((k, v) -> System.out.println("  " + k + ": " + v.size() + " orders"));

        // downstream = counting()
        Map<String, Long> counts = ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer, Collectors.counting()));
        System.out.println("  counts = " + counts);

        // downstream = summingDouble — total spent per customer
        Map<String, Double> totals = ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer,
                        Collectors.summingDouble(Order::total)));
        System.out.println("  totals = " + totals);

        // 3-arg form: TreeMap for sorted output
        Map<String, Long> sorted = ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer, TreeMap::new, Collectors.counting()));
        System.out.println("  sorted (TreeMap) = " + sorted);
    }

    /*
    partitioningBy

    Collectors.partitioningBy(predicate) is groupingBy with a fixed key set { true, false }. The result is always a
    2-entry map; both keys are present even when one bucket is empty.

    When you need exactly a yes/no split, prefer partitioningBy over groupingBy — it conveys intent and guarantees
    the empty key.
    */
    static void partitioningBy() {
        System.out.println("[Section 3] partitioningBy");

        Map<Boolean, List<Order>> bigVsSmall = ORDERS.stream()
                .collect(Collectors.partitioningBy(o -> o.total() >= 100));
        System.out.println("  big   (>=100): " + bigVsSmall.get(true).size());
        System.out.println("  small (<100):  " + bigVsSmall.get(false).size());

        // partitioningBy + downstream
        Map<Boolean, Long> counts = ORDERS.stream()
                .collect(Collectors.partitioningBy(o -> o.total() >= 100, Collectors.counting()));
        System.out.println("  counts = " + counts);
    }

    /*
    Downstream composition

    The "groupingBy + downstream" combinator covers most real-world aggregations:

    - groupingBy(K, counting()) — histogram.
    - groupingBy(K, summingDouble(toDouble)) — total per group.
    - groupingBy(K, mapping(extractor, toList())) — group, then transform each element to something simpler before
      collecting.
    - groupingBy(K, reducing(BinaryOperator)) — fold each group into a single value (e.g., the max-priced order per
      customer).
    - groupingBy(K1, groupingBy(K2, ...)) — nested grouping.

    mapping is the under-rated one: it is a collector adapter that applies a transformation before the downstream
    collector sees the element. Without it, you would have to map before the groupingBy and lose access to the
    original element.
    */
    static void downstreamComposition() {
        System.out.println("[Section 4] downstream composition");

        // groupingBy + mapping + toList — group customers' SKUs
        Map<String, List<String>> skusByCustomer = ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer,
                        Collectors.mapping(Order::sku, Collectors.toList())));
        System.out.println("  skus per customer  = " + skusByCustomer);

        // groupingBy + reducing — biggest order per customer
        Map<String, Order> biggestPerCustomer = ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer,
                        Collectors.collectingAndThen(
                                Collectors.maxBy((a, b) -> Double.compare(a.total(), b.total())),
                                java.util.Optional::orElseThrow)));
        biggestPerCustomer.forEach((c, o) ->
                System.out.printf("  biggest for %s: total=%.2f%n", c, o.total()));

        // nested groupingBy: customer -> sku -> total quantity
        Map<String, Map<String, Integer>> nested = ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer,
                        Collectors.groupingBy(Order::sku,
                                Collectors.summingInt(Order::quantity))));
        System.out.println("  nested = " + nested);
    }

    /*
    Custom Collector

    A Collector<T, A, R> has four moving parts plus characteristics:

    - Supplier<A> supplier — create an empty accumulator.
    - BiConsumer<A, T> accumulator — fold one element into the accumulator.
    - BinaryOperator<A> combiner — merge two accumulators (used in parallel).
    - Function<A, R> finisher — turn the final accumulator into the result.
    - Set<Characteristics> — CONCURRENT, UNORDERED, IDENTITY_FINISH.

    The Collector.of(supplier, accumulator, combiner [, finisher][, chars]) factory lets you build one in a single
    expression for cases where extending Collector interface is overkill.

    Most "I need a custom collector" needs are actually solved by reduce(...) or Collectors.collectingAndThen(...) —
    try those first.
    */
    static void customCollector() {
        System.out.println("[Section 5] custom Collector");

        // Build a delimiter-separated string of customer names — the long way,
        // using Collector.of with a StringJoiner accumulator.
        Collector<String, StringJoiner, String> arrowJoin = Collector.of(
                () -> new StringJoiner(" → ", "[", "]"),
                StringJoiner::add,
                StringJoiner::merge,                                     // combiner (parallel-safe)
                StringJoiner::toString,                                  // finisher
                Collector.Characteristics.UNORDERED);

        String chain = ORDERS.stream()
                .map(Order::customer)
                .distinct()
                .collect(arrowJoin);
        System.out.println("  customer chain = " + chain);

        // Idiomatic equivalent for this exact case is just Collectors.joining;
        // Collector.of pays off for genuinely custom accumulation.
        String idiomatic = ORDERS.stream().map(Order::customer).distinct()
                .collect(Collectors.joining(" → ", "[", "]"));
        System.out.println("  idiomatic       = " + idiomatic);
    }

    public static void main(String[] args) {
        basicCollectors();
        groupingBy();
        partitioningBy();
        downstreamComposition();
        customCollector();
        System.out.println("Mod005Collectors finished");
    }
}
