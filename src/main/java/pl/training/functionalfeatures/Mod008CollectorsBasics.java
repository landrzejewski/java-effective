package pl.training.functionalfeatures;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Mod008CollectorsBasics {

    private Mod008CollectorsBasics() {}

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
    Materialising a stream

    A Collector is a recipe for accumulating elements into a container. The everyday ones:

    - Stream.toList() (Java 16+)          - the short form. Unmodifiable, and it ALLOWS null elements.
    - Collectors.toList()                 - javadoc guarantees neither the type nor the mutability of the result
                                            ("no guarantees on the type, mutability, serializability, or
                                            thread-safety"). In practice today it is an ArrayList, but do not
                                            write code that depends on that. If you need a mutable list, say so:
                                            Collectors.toCollection(ArrayList::new).
    - Collectors.toUnmodifiableList/Set/Map - immutable, and they REJECT null with NullPointerException.
    - Collectors.toSet()                  - a Set with no ordering guarantee.
    - Collectors.toCollection(factory)    - you choose the exact container (TreeSet, LinkedList, EnumSet...).
    - Collectors.joining(sep, prefix, suffix) - for CharSequence elements.

    The null behaviour is the one difference that actually bites: Stream.toList() accepts nulls,
    toUnmodifiableList() does not.
    */
    static void materialising() {
        System.out.println("[Section 1] materialising a stream");

        var customers = ORDERS.stream().map(Order::customer).distinct();

        // Explicitly mutable, when you need it.
        var mutable = ORDERS.stream().map(Order::customer).distinct()
                .collect(Collectors.toCollection(java.util.ArrayList::new));
        mutable.add("dave");
        System.out.println("  toCollection(ArrayList::new) -> " + mutable + "  (add succeeded)");

        // Stream.toList() is unmodifiable - the difference is only visible when you try to mutate.
        var immutable = customers.toList();
        try {
            immutable.add("dave");
        } catch (UnsupportedOperationException e) {
            System.out.println("  Stream.toList()              -> " + immutable + "  (add threw "
                    + e.getClass().getSimpleName() + ")");
        }

        // Null handling: the two "immutable list" collectors disagree.
        System.out.println("  Stream.of(a,null).toList()   -> " + Stream.of("a", null).toList());
        try {
            Stream.of("a", (String) null).collect(Collectors.toUnmodifiableList());
        } catch (NullPointerException e) {
            System.out.println("  toUnmodifiableList()         -> NullPointerException on the null element");
        }

        System.out.println("  joining(\", \", \"[\", \"]\")       -> " + ORDERS.stream()
                .map(Order::sku).distinct().collect(Collectors.joining(", ", "[", "]")));
    }

    /*
    toMap

    Collectors.toMap(keyFn, valueFn) is the direct one - and it throws IllegalStateException the moment two
    elements produce the same key. That is a deliberate design choice: silently keeping one of two values is
    almost never what you meant.

    Three overloads, in increasing capability:

    - toMap(k, v)                    - duplicate key is an error.
    - toMap(k, v, mergeFn)           - mergeFn(existing, incoming) decides. (a, b) -> b is "last wins",
                                       (a, b) -> a is "first wins", Double::sum aggregates.
    - toMap(k, v, mergeFn, mapFactory) - also pick the map type: LinkedHashMap to keep encounter order,
                                       TreeMap to sort by key, EnumMap for enum keys.

    A second trap: toMap rejects a null VALUE with NullPointerException, unlike HashMap.put. Map the value to a
    sentinel or use groupingBy if nulls are legitimate.
    */
    static void toMapCollector() {
        System.out.println("[Section 2] toMap");

        // Duplicate keys with the 2-arg form: SKU-001 and SKU-002 each appear twice.
        try {
            ORDERS.stream().collect(Collectors.toMap(Order::sku, Order::total));
        } catch (IllegalStateException e) {
            System.out.println("  2-arg form on duplicate keys -> IllegalStateException (by design)");
        }

        // 3-arg: say what a duplicate means.
        Map<String, Double> totalsBySku = ORDERS.stream()
                .collect(Collectors.toMap(Order::sku, Order::total, Double::sum));
        System.out.println("  merge with Double::sum       -> " + totalsBySku);

        // 4-arg: also choose the map implementation.
        Map<String, Double> sortedBySku = ORDERS.stream()
                .collect(Collectors.toMap(Order::sku, Order::total, Double::sum, TreeMap::new));
        System.out.println("  ... into a TreeMap           -> " + sortedBySku);
    }

    /*
    groupingBy

    groupingBy(keyFn) is the workhorse: it produces Map<K, List<T>>, and unlike toMap it never complains about
    duplicates - collisions are the whole point.

    - groupingBy(keyFn)                        - downstream defaults to toList().
    - groupingBy(keyFn, downstream)            - replace toList() with counting(), summingDouble(), mapping(),
                                                 averagingInt(), another groupingBy(), anything.
    - groupingBy(keyFn, mapFactory, downstream) - also choose the map type. Note the argument ORDER here:
                                                 factory in the middle, downstream last.

    Empty groups never appear: a key exists only if at least one element produced it. When you need the empty
    bucket too, that is partitioningBy (Section 4) or a pre-seeded map.
    */
    static void groupingBy() {
        System.out.println("[Section 3] groupingBy");

        Map<String, List<Order>> byCustomer = ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer));
        System.out.println("  default (toList) -> " + byCustomer.get("alice").size() + " orders for alice");

        System.out.println("  counting()       -> " + ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer, Collectors.counting())));

        System.out.println("  summingDouble()  -> " + ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer, Collectors.summingDouble(Order::total))));

        System.out.println("  averagingInt()   -> " + ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer, Collectors.averagingInt(Order::quantity))));

        // 3-arg form: keys come out sorted because the map is a TreeMap.
        System.out.println("  into a TreeMap   -> " + ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer, TreeMap::new, Collectors.counting())));
    }

    /*
    partitioningBy

    partitioningBy(predicate) is groupingBy with a fixed key set of exactly { false, true }. The difference that
    matters: BOTH keys are always present, even when one side is empty. groupingBy(o -> o.total() >= 100) can
    hand you a map with a single entry, and then get(true) returns null.

    Use partitioningBy whenever the split is a yes/no question: it states the intent and removes a null check.
    It takes a downstream collector too, exactly like groupingBy.
    */
    static void partitioningBy() {
        System.out.println("[Section 4] partitioningBy");

        Map<Boolean, List<Order>> bigVsSmall = ORDERS.stream()
                .collect(Collectors.partitioningBy(o -> o.total() >= 100));
        System.out.println("  >=100: " + bigVsSmall.get(true).size() + ", <100: " + bigVsSmall.get(false).size());

        // The guarantee: a predicate nothing matches still yields both keys.
        Map<Boolean, Long> impossible = ORDERS.stream()
                .collect(Collectors.partitioningBy(o -> o.total() > 1_000_000, Collectors.counting()));
        System.out.println("  no match at all -> " + impossible + "  (both keys present)");

        // groupingBy on the same predicate loses the empty side.
        System.out.println("  groupingBy      -> " + ORDERS.stream()
                .collect(Collectors.groupingBy(o -> o.total() > 1_000_000, Collectors.counting()))
                + "  (get(true) would be null)");
    }

    /*
    Downstream adapters

    The downstream slot accepts any Collector, including ones whose job is to ADAPT another collector:

    - mapping(fn, downstream)            - transform each element before the downstream sees it. This is the one
                                           that unlocks groupingBy: you group on the whole element but collect
                                           only one field of it. Mapping BEFORE the groupingBy would have thrown
                                           the grouping key away.
    - collectingAndThen(coll, finisher)  - post-process the finished result. The standard way to unwrap the
                                           Optional that maxBy/minBy/reducing produce, or to freeze a list.
    - reducing(identity, fn, op)         - fold each group into a single value.
    - maxBy(cmp) / minBy(cmp)            - the extreme element of each group, as an Optional.

    Nested groupingBy is just another downstream: groupingBy(k1, groupingBy(k2, ...)).
    */
    static void downstreamAdapters() {
        System.out.println("[Section 5] downstream adapters");

        // mapping: group by customer, but keep only the SKUs.
        Map<String, List<String>> skusByCustomer = ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer,
                        Collectors.mapping(Order::sku, Collectors.toList())));
        System.out.println("  mapping           -> " + skusByCustomer);

        // reducing: fold each group into one number. The 3-arg form needs no Optional unwrapping.
        Map<String, Double> heaviestPerCustomer = ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer,
                        Collectors.reducing(0.0, Order::total, Double::max)));
        System.out.println("  reducing          -> " + heaviestPerCustomer);

        // maxBy returns an Optional; collectingAndThen unwraps it so the map value is an Order.
        Map<String, Order> biggestPerCustomer = ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(java.util.Comparator.comparingDouble(Order::total)),
                                Optional::orElseThrow)));
        biggestPerCustomer.forEach((c, o) -> System.out.printf(Locale.ROOT, "  biggest for %-6s -> %s%n", c, o.sku()));

        // Nested grouping: customer -> sku -> quantity.
        Map<String, Map<String, Integer>> nested = ORDERS.stream()
                .collect(Collectors.groupingBy(Order::customer,
                        Collectors.groupingBy(Order::sku, Collectors.summingInt(Order::quantity))));
        System.out.println("  nested groupingBy -> " + nested);
    }

    public static void main(String[] args) {
        materialising();
        toMapCollector();
        groupingBy();
        partitioningBy();
        downstreamAdapters();
        System.out.println("Mod008CollectorsBasics finished");
    }
}
