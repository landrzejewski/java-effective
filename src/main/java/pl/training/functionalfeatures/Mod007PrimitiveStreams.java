package pl.training.functionalfeatures;

import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public final class Mod007PrimitiveStreams {

    private Mod007PrimitiveStreams() {}

    record Product(String sku, double price, int stock) {}

    private static final List<Product> CATALOG = List.of(
            new Product("SKU-001",   9.99, 120),
            new Product("SKU-002",  29.50,  18),
            new Product("SKU-003", 199.00,   3),
            new Product("SKU-004",   2.50, 940));

    /*
    Why primitive streams exist

    Stream<Integer> stores boxed Integers. Every element is a heap object, every arithmetic step unboxes and
    reboxes, and the values are scattered across memory instead of laid out contiguously. IntStream, LongStream
    and DoubleStream keep the elements as primitives from the source to the terminal operation.

    Two payoffs, in this order of importance:

    1. Ergonomics. sum(), average(), max(), summaryStatistics() and range() simply do not exist on Stream<T>,
       because summing an arbitrary T is meaningless. On IntStream they are one call.
    2. Performance. No allocation per element. This matters when N is large and the per-element work is small;
       for a few hundred elements the difference is noise, so reach for it when a profiler says so - or when the
       primitive API is simply the more readable one.

    Only three specialisations exist: int, long, double. There is no CharStream or ShortStream - String.chars()
    returns an IntStream, which is why casting back to char comes up so often.
    */
    static void whyPrimitiveStreams() {
        System.out.println("[Section 1] why primitive streams");

        // Boxed: sum has to be spelled out with a reduce, and every element is an Integer object.
        int boxedSum = Stream.of(1, 2, 3, 4, 5).reduce(0, Integer::sum);

        // Primitive: sum() is built in, and nothing is allocated.
        int primitiveSum = IntStream.of(1, 2, 3, 4, 5).sum();

        System.out.println("  Stream<Integer>.reduce = " + boxedSum);
        System.out.println("  IntStream.sum()        = " + primitiveSum);

        // String.chars() is an IntStream - there is no CharStream.
        var letters = "stream".chars().mapToObj(c -> String.valueOf((char) c)).toList();
        System.out.println("  \"stream\".chars()       = " + letters);
    }

    /*
    Creating primitive streams

    - IntStream.range(start, endExclusive) / rangeClosed(start, endInclusive) - the counted loop as a stream.
    - IntStream.of(...), Arrays.stream(int[]) - from literals or an array.
    - stream.mapToInt(fn) / mapToLong(fn) / mapToDouble(fn) - project an object stream onto a numeric field.
    - IntStream.iterate / generate - same as on Stream, unboxed.

    range/rangeClosed replace the index-based for loop when the index itself is the data. When the index is only
    used to reach into a collection, iterate the collection instead - indexing through a stream is a step
    backwards.
    */
    static void creatingPrimitiveStreams() {
        System.out.println("[Section 2] creating primitive streams");

        System.out.println("  rangeClosed(1,100).sum() = " + IntStream.rangeClosed(1, 100).sum());
        System.out.println("  range(0,5).toArray()     = " + Arrays.toString(IntStream.range(0, 5).toArray()));

        // mapToDouble: project the object stream onto one numeric component.
        System.out.printf(Locale.ROOT, "  catalog value            = %.2f%n",
                CATALOG.stream().mapToDouble(p -> p.price() * p.stock()).sum());

        // Legitimate use of an index: pairing position with value.
        var numbered = IntStream.range(0, CATALOG.size())
                .mapToObj(i -> (i + 1) + ". " + CATALOG.get(i).sku())
                .toList();
        System.out.println("  range as an index        = " + numbered);
    }

    /*
    Numeric terminal operations

    IntStream/LongStream/DoubleStream add aggregations that Stream<T> cannot have:

    - sum()                - returns int / long / double. Watch for int overflow: IntStream.sum() wraps around
                             silently. Use asLongStream().sum() when the total may not fit.
    - average()            - returns OptionalDouble, because an empty stream has no average.
    - min() / max()        - return OptionalInt / OptionalLong / OptionalDouble, no Comparator needed.
    - count()              - long, same as on Stream.
    - summaryStatistics()  - see the next section.

    OptionalInt and friends are separate types from Optional<Integer>: same idea, no boxing, and a smaller API
    (getAsInt, orElse, ifPresent - no map/flatMap).
    */
    static void numericTerminals() {
        System.out.println("[Section 3] numeric terminal operations");

        var prices = CATALOG.stream().mapToDouble(Product::price);
        System.out.printf(Locale.ROOT, "  average price = %.2f%n", prices.average().orElse(0));

        System.out.println("  max stock     = " + CATALOG.stream().mapToInt(Product::stock).max().orElseThrow());

        // Overflow demonstration: the int sum wraps, the long sum does not.
        int overflowing = IntStream.of(Integer.MAX_VALUE, 1).sum();
        long safe = IntStream.of(Integer.MAX_VALUE, 1).asLongStream().sum();
        System.out.println("  int sum overflows to  " + overflowing);
        System.out.println("  asLongStream().sum()  " + safe);
    }

    /*
    Summary statistics

    summaryStatistics() computes count, sum, min, max and average in a SINGLE pass. Running the five terminal
    operations separately would need five streams over the source - and a stream is one-shot, so you would have
    to rebuild it each time.

    - IntSummaryStatistics / LongSummaryStatistics / DoubleSummaryStatistics.
    - The collector form works straight on Stream<T>: Collectors.summarizingInt(toIntFn), summarizingDouble(...).
      That version composes as a groupingBy downstream, which the stream method cannot (Mod008).
    - The objects are mutable and combinable (accept / combine), which is why they parallelise for free.
    */
    static void summaryStatistics() {
        System.out.println("[Section 4] summary statistics - one pass, five numbers");

        IntSummaryStatistics stock = CATALOG.stream().mapToInt(Product::stock).summaryStatistics();
        System.out.printf(Locale.ROOT, "  stock: count=%d sum=%d min=%d max=%d avg=%.1f%n",
                stock.getCount(), stock.getSum(), stock.getMin(), stock.getMax(), stock.getAverage());

        // The collector form - same data, but it can sit downstream of a groupingBy.
        DoubleSummaryStatistics byCollector =
                CATALOG.stream().collect(Collectors.summarizingDouble(Product::price));
        System.out.printf(Locale.ROOT, "  price: min=%.2f max=%.2f avg=%.2f%n",
                byCollector.getMin(), byCollector.getMax(), byCollector.getAverage());
    }

    /*
    Crossing between object and primitive streams

    - Stream<T> -> IntStream        : mapToInt / mapToLong / mapToDouble
    - IntStream -> Stream<Integer>  : boxed()
    - IntStream -> Stream<R>        : mapToObj(fn) - goes straight to R without boxing to Integer first
    - IntStream -> LongStream       : asLongStream() (also asDoubleStream) - widening, still unboxed
    - IntStream -> Stream<T> then back: possible, but every hop costs a box/unbox

    Rule: cross once, at the point where the work changes shape. boxed().map(...) where mapToObj(...) would do
    is the most common wasted hop - boxed() allocates an Integer that the very next operation discards.
    */
    static void interConversion() {
        System.out.println("[Section 5] conversions");

        // boxed(): needed when an OBJECT collector is the destination.
        var asList = IntStream.rangeClosed(1, 5).boxed().collect(Collectors.toList());
        System.out.println("  boxed().collect(toList) = " + asList);

        // mapToObj(): one hop instead of two when the destination is not Integer.
        var labels = IntStream.rangeClosed(1, 3).mapToObj(i -> "label-" + i).toList();
        System.out.println("  mapToObj (no boxing)    = " + labels);

        // Widening without boxing.
        System.out.println("  asLongStream().sum()    = " + IntStream.rangeClosed(1, 1000).asLongStream().sum());

        // Back the other way.
        System.out.println("  mapToInt(String::length)= "
                + Stream.of("alice", "bob", "carla").mapToInt(String::length).sum());

        System.out.println("  LongStream.max()        = " + LongStream.of(10, 20, 5, 30).max().orElseThrow());
    }

    public static void main(String[] args) {
        whyPrimitiveStreams();
        creatingPrimitiveStreams();
        numericTerminals();
        summaryStatistics();
        interConversion();
        System.out.println("Mod007PrimitiveStreams finished");
    }
}
