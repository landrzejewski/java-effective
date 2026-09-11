package pl.training.functionalfeatures;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Mod009CollectorsAdvanced {

    private Mod009CollectorsAdvanced() {}

    record Order(String customer, String sku, int quantity, double unitPrice, List<String> tags) {
        double total() { return quantity * unitPrice; }
    }

    private static final List<Order> ORDERS = List.of(
            new Order("alice", "SKU-001",  2,  19.99, List.of("retail", "priority")),
            new Order("bob",   "SKU-002",  1, 199.00, List.of("wholesale")),
            new Order("alice", "SKU-003", 10,   2.50, List.of("retail")),
            new Order("carla", "SKU-001",  5,  19.99, List.of("retail", "gift")),
            new Order("bob",   "SKU-004",  3,  49.50, List.of("wholesale", "priority")),
            new Order("alice", "SKU-002",  1, 199.00, List.of("gift")));

    /*
    teeing (Java 12)

    Collectors.teeing(collectorA, collectorB, merger) runs TWO collectors over the same stream in a single pass
    and combines their results.

    Before it existed, computing two aggregates meant either two pipelines over the source (and a stream is
    one-shot, so you had to rebuild it) or a hand-written collector. teeing does it in one traversal, which
    matters when the source is expensive: a database cursor, a file, a network response.

    The classic uses are min-and-max, count-and-sum (an average you compute yourself), and total-and-subtotal.
    It composes like any other collector, so it can sit downstream of a groupingBy.
    */
    static void teeing() {
        System.out.println("[Section 1] teeing: two aggregates, one pass");

        record Span(double cheapest, double dearest) {}

        Span span = ORDERS.stream().collect(Collectors.teeing(
                Collectors.collectingAndThen(Collectors.minBy(Comparator.comparingDouble(Order::total)),
                        o -> o.orElseThrow().total()),
                Collectors.collectingAndThen(Collectors.maxBy(Comparator.comparingDouble(Order::total)),
                        o -> o.orElseThrow().total()),
                Span::new));
        System.out.printf(Locale.ROOT, "  min/max in one pass  = %.2f .. %.2f%n", span.cheapest(), span.dearest());

        // Downstream of a groupingBy: count and sum per customer, merged into a formatted string.
        Map<String, String> perCustomer = ORDERS.stream().collect(Collectors.groupingBy(Order::customer,
                Collectors.teeing(
                        Collectors.counting(),
                        Collectors.summingDouble(Order::total),
                        (count, sum) -> String.format(Locale.ROOT, "%d orders / %.2f", count, sum))));
        System.out.println("  teeing under groupingBy = " + perCustomer);
    }

    /*
    filtering and flatMapping (Java 9)

    Two more collector adapters, and the reason they exist is subtle.

    filtering(predicate, downstream) is NOT the same as stream.filter(...).collect(groupingBy(...)). Filtering
    upstream removes the element entirely, so a group whose every element failed the predicate never appears.
    filtering() applies the test INSIDE the group, so the group survives with an empty list. When you are
    reporting per key, that difference is the whole point.

    flatMapping(fn, downstream) applies a one-to-many function inside the group. Use it when each element
    carries a collection and you want the group to collect the flattened contents.
    */
    static void filteringAndFlatMapping() {
        System.out.println("[Section 2] filtering vs filter, and flatMapping");

        // filter upstream: carla disappears entirely, because her only order is under 100.
        var upstream = ORDERS.stream()
                .filter(o -> o.total() >= 100)
                .collect(Collectors.groupingBy(Order::customer, Collectors.counting()));
        System.out.println("  .filter() upstream   = " + upstream + "   (carla is gone)");

        // filtering downstream: every customer keeps a row, and carla's is honestly zero.
        var downstream = ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer,
                        Collectors.filtering(o -> o.total() >= 100, Collectors.counting())));
        System.out.println("  filtering() downstream = " + downstream + "   (carla reported as 0)");

        // flatMapping: each order carries a tag list; collect the union of tags per customer.
        Map<String, Set<String>> tagsByCustomer = ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer,
                        Collectors.flatMapping(o -> o.tags().stream(), Collectors.toCollection(TreeSet::new))));
        System.out.println("  flatMapping tags       = " + tagsByCustomer);
    }

    /*
    mapMulti (Java 16)

    stream.mapMulti(fn) is a one-to-many transformation like flatMap, but instead of RETURNING a stream per
    element it hands you a consumer and lets you push zero or more values into it.

    Why bother when flatMap exists: flatMap allocates a Stream object for every single element. When the
    expansion factor is small (usually 0 or 1 result, occasionally a few) that allocation dominates. mapMulti
    pushes straight into the downstream and allocates nothing.

    It is also simply easier to write when the expansion is imperative, for example a recursive walk, because
    you can call the consumer from inside a loop or a nested method instead of building a stream to return.

    The primitive variants are mapMultiToInt / mapMultiToLong / mapMultiToDouble. The explicit type witness
    (.<String>mapMulti) is usually required because the compiler cannot infer the result type from a consumer.
    */
    static void mapMulti() {
        System.out.println("[Section 3] mapMulti");

        // flatMap version: one Stream allocated per order.
        var viaFlatMap = ORDERS.stream().flatMap(o -> o.tags().stream()).distinct().sorted().toList();

        // mapMulti version: same result, nothing allocated per element.
        var viaMapMulti = ORDERS.stream()
                .<String>mapMulti((o, sink) -> o.tags().forEach(sink))
                .distinct().sorted().toList();

        System.out.println("  flatMap  = " + viaFlatMap);
        System.out.println("  mapMulti = " + viaMapMulti);

        // Where mapMulti really wins: conditional expansion, mostly emitting nothing.
        var flagged = ORDERS.stream()
                .<String>mapMulti((o, sink) -> {
                    if (o.total() >= 100) sink.accept(o.sku() + " (high value)");
                    if (o.tags().contains("priority")) sink.accept(o.sku() + " (priority)");
                })
                .toList();
        System.out.println("  0-or-many per element = " + flagged);
    }

    /*
    Local records as named tuples

    A stream pipeline often needs to carry two or three values together for a few operations: a key with its
    total, an element with its computed score. The old options were Map.Entry (meaningless names, limited to
    two), an Object[] (no types), or a top-level class nobody else uses.

    Since Java 16 a record can be declared INSIDE a method. It is scoped to that method, it gets accessors,
    equals, hashCode and toString for free, and its component names document the pipeline. It is also implicitly
    static, so it captures nothing from the enclosing scope.

    This is the cleanest answer to "I need a tuple here" and it composes with record patterns (Mod015).
    */
    static void localRecordsAsTuples() {
        System.out.println("[Section 4] local records as named tuples");

        // Declared here, used here, invisible everywhere else. Two of them: one to carry the pair
        // that teeing produces, one to carry the finished row.
        record Totals(double revenue, long orderCount) {}
        record CustomerRevenue(String customer, double revenue, long orderCount) {
            double averageOrder() { return revenue / orderCount; }
        }

        List<CustomerRevenue> ranking = ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer,
                        Collectors.teeing(
                                Collectors.summingDouble(Order::total),
                                Collectors.counting(),
                                Totals::new)))
                .entrySet().stream()
                .map(e -> new CustomerRevenue(e.getKey(), e.getValue().revenue(), e.getValue().orderCount()))
                .sorted(Comparator.comparingDouble(CustomerRevenue::revenue).reversed())
                .toList();

        ranking.forEach(r -> System.out.printf(Locale.ROOT, "  %-6s revenue=%7.2f orders=%d avg=%7.2f%n",
                r.customer(), r.revenue(), r.orderCount(), r.averageOrder()));

        // Compare with the Map.Entry version of the same line: e.getKey() and e.getValue() say nothing
        // about what they hold, and there is no room for a third component or a derived method.
    }

    /*
    Writing a Collector

    Collector.of(supplier, accumulator, combiner [, finisher] [, characteristics]) builds one inline:

    - supplier    : create an empty accumulator.
    - accumulator : fold one element into it.
    - combiner    : merge two accumulators. Only used when the stream runs in parallel, but it must be correct
                    anyway: it is part of the contract, and a wrong one is a bug that only appears under load.
    - finisher    : turn the accumulator into the result. Omit it when they are the same type.
    - characteristics: promises you make to the implementation. Get these wrong and the result is wrong.
        UNORDERED       - the result does not depend on encounter order. TRUE for a Set or a sum;
                          FALSE for anything that concatenates or keeps a list. Declaring it lets the
                          implementation reorder work, so an incorrect claim corrupts parallel results.
        IDENTITY_FINISH - the finisher is the identity, so it can be skipped. Only valid when A and R
                          are the same type and you passed no finisher.
        CONCURRENT      - a single accumulator may be shared across threads. Requires a thread-safe
                          accumulator, and only helps together with UNORDERED.

    Reach for this last. Most "custom collector" needs are covered by collectingAndThen, teeing, reducing, or
    simply collecting into a list and post-processing it.
    */
    static void writingACollector() {
        System.out.println("[Section 5] writing a Collector");

        // A joining collector built by hand. Note: NO Unordered characteristic. Concatenation depends
        // on the order of elements, so promising otherwise would be a lie the runtime is allowed to act on.
        Collector<String, StringJoiner, String> arrowJoin = Collector.of(
                () -> new StringJoiner(" > ", "[", "]"),
                StringJoiner::add,
                StringJoiner::merge,
                StringJoiner::toString);

        System.out.println("  hand-written = " + ORDERS.stream().map(Order::customer).distinct().collect(arrowJoin));
        System.out.println("  Collectors.joining does the same, so prefer it for this exact job");

        // A genuinely custom one: keep the FIRST order seen per SKU, preserving encounter order.
        // Nothing in java.util.stream.Collectors does this in a single step.
        // Note the finisher: Collections::unmodifiableMap, NOT Map::copyOf. Map.copyOf makes no promise
        // about iteration order (the JDK deliberately randomises it), so it would throw away the very
        // ordering the LinkedHashMap accumulator exists to preserve.
        Collector<Order, LinkedHashMap<String, Order>, Map<String, Order>> firstPerSku = Collector.of(
                LinkedHashMap::new,
                (acc, o) -> acc.putIfAbsent(o.sku(), o),
                (a, b) -> { b.forEach(a::putIfAbsent); return a; },
                java.util.Collections::unmodifiableMap);

        System.out.println("  first order per SKU = " + ORDERS.stream().collect(firstPerSku)
                .entrySet().stream()
                .map(e -> e.getKey() + "->" + e.getValue().customer())
                .sorted().toList());
    }

    /*
    Choosing a collector

    | Need                                    | Reach for                                  |
    |-----------------------------------------|--------------------------------------------|
    | a container of the elements             | toList / toSet / toCollection(factory)     |
    | a keyed view, keys unique               | toMap(k, v, merge)                         |
    | a keyed view, keys collide              | groupingBy(k, downstream)                  |
    | a yes/no split, both sides guaranteed   | partitioningBy(p, downstream)              |
    | transform inside a group                | mapping / flatMapping / filtering          |
    | two aggregates in one pass              | teeing                                     |
    | post-process the finished result        | collectingAndThen                          |
    | one-to-many with little or no expansion | mapMulti (an intermediate op, not a collector) |
    | none of the above                       | Collector.of, and check the characteristics |
    */
    static void choosingACollector() {
        System.out.println("[Section 6] one dataset, five questions");

        System.out.println("  distinct SKUs           = " + ORDERS.stream()
                .map(Order::sku).collect(Collectors.toCollection(TreeSet::new)));
        System.out.println("  revenue per SKU         = " + ORDERS.stream()
                .collect(Collectors.toMap(Order::sku, Order::total, Double::sum, java.util.TreeMap::new)));
        System.out.println("  customers per tag       = " + ORDERS.stream()
                .flatMap(o -> o.tags().stream().map(t -> Map.entry(t, o.customer())))
                .collect(Collectors.groupingBy(Map.Entry::getKey, java.util.TreeMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toCollection(TreeSet::new)))));
        System.out.println("  wholesale vs retail     = " + ORDERS.stream()
                .collect(Collectors.partitioningBy(o -> o.tags().contains("wholesale"), Collectors.counting())));
        System.out.println("  report line             = " + ORDERS.stream()
                .collect(Collectors.teeing(Collectors.counting(), Collectors.summingDouble(Order::total),
                        (n, sum) -> String.format(Locale.ROOT, "%d orders worth %.2f", n, sum))));
    }

    public static void main(String[] args) {
        teeing();
        filteringAndFlatMapping();
        mapMulti();
        localRecordsAsTuples();
        writingACollector();
        choosingACollector();
        System.out.println("Mod009CollectorsAdvanced finished");
    }
}
