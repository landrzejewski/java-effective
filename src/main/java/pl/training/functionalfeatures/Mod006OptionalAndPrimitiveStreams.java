package pl.training.functionalfeatures;

import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public final class Mod006OptionalAndPrimitiveStreams {

    private Mod006OptionalAndPrimitiveStreams() {}

    record User(String id, String displayName) {}
    record Product(String sku, double price) {}

    private static final Map<String, User> USERS = Map.of(
            "u-1", new User("u-1", "alice"),
            "u-2", new User("u-2", "bob"));

    private static final List<Product> CATALOG = List.of(
            new Product("SKU-001",   9.99),
            new Product("SKU-002",  29.50),
            new Product("SKU-003", 199.00),
            new Product("SKU-004",   2.50));

    private static Optional<User> findUser(String id) {
        return Optional.ofNullable(USERS.get(id));
    }

    /*
    Optional<T> semantics

    Optional<T> is a return-type-only wrapper that says "this method may return no value". It exists to make absence
    explicit at compile time — preventing a class of NullPointerExceptions and forcing the caller to handle the empty
    case.

    - Construct with Optional.of(value) (throws NPE if null), Optional.ofNullable(value), or Optional.empty().
    - Do not use Optional for fields, parameters, or collection elements. Storing Optional<List<X>> is a bug —
      return an empty list instead. A field of type Optional<X> adds an allocation per instance and breaks
      serialisation.
    - An optional is also not a substitute for null in maps. The standard idiom is Optional.ofNullable(map.get(key))
      at the boundary, plain null checks inside.
    */
    static void optionalSemantics() {
        System.out.println("[Section 1] Optional construction");

        Optional<User> hit = findUser("u-1");
        Optional<User> miss = findUser("u-99");
        System.out.println("  hit  = " + hit);
        System.out.println("  miss = " + miss);
    }

    /*
    Optional API

    Functional combinators (no need to call isPresent + get):

    - map(fn) — apply a function if present.
    - flatMap(fn) — for chaining methods that themselves return Optional.
    - filter(p) — narrow to empty if the predicate fails.
    - orElse(default) — return either the value or a default. Eager.
    - orElseGet(supplier) — same but lazy.
    - orElseThrow() / orElseThrow(exFactory) — turn empty into an exception.
    - ifPresent(consumer), ifPresentOrElse(consumer, runnable).
    - or(supplier) — if empty, fall back to another Optional from the supplier (chains alternatives).
    - stream() — Optional<T> -> Stream<T> of size 0 or 1; useful with flatMap.
    */
    static void optionalApi() {
        System.out.println("[Section 2] Optional API");

        // map: derive a string from the User if present
        String name = findUser("u-1").map(User::displayName).orElse("unknown");
        System.out.println("  name = " + name);

        // flatMap: chain through another Optional-returning method (here a fake one)
        Optional<String> maybeUpper = findUser("u-2")
                .flatMap(u -> Optional.of(u.displayName().toUpperCase()));
        System.out.println("  flatMap upper = " + maybeUpper.orElseThrow());

        // filter
        boolean isAlice = findUser("u-1").filter(u -> u.displayName().equals("alice")).isPresent();
        System.out.println("  isAlice = " + isAlice);

        // ifPresent / ifPresentOrElse — replace `if (isPresent) get()`
        findUser("u-1").ifPresentOrElse(
                u -> System.out.println("  found " + u.displayName()),
                () -> System.out.println("  user not found"));

        // or — fall back to another Optional
        var primary = Optional.<User>empty();
        var fallback = primary.or(() -> findUser("u-2"));
        System.out.println("  or() chain = " + fallback);

        // stream — turn 0/1 Optionals into a stream and flatMap them away
        var allDisplayNames = Stream.of("u-1", "u-2", "u-99")
                .map(Mod006OptionalAndPrimitiveStreams::findUser)
                .flatMap(Optional::stream)
                .map(User::displayName)
                .toList();
        System.out.println("  flatMap(Optional::stream) = " + allDisplayNames);
    }

    /*
    Optional anti-patterns

    The three most common bad smells:

    1. if (opt.isPresent()) doSomething(opt.get()); — call ifPresent instead. get() should mostly disappear from
       your codebase outside of asserts and orElseThrow.
    2. Returning Optional<List<X>> — return an empty list instead. Empty collection is already "no result"; wrapping
       it in Optional adds nothing and forces every caller to unwrap.
    3. Using Optional as a field, constructor parameter, or method parameter — it is not designed for it. Optional
       carries 16+ bytes of overhead per instance and many libraries (Jackson, Hibernate, JPA) interact poorly with
       it.
    */
    static void optionalAntiPatterns() {
        System.out.println("[Section 3] Optional anti-patterns");

        // ANTI: if (isPresent) get()
        Optional<User> u = findUser("u-1");
        if (u.isPresent()) {                                    // smell
            System.out.println("  bad style:  " + u.get().displayName());
        }
        // PREFER:
        u.ifPresent(x -> System.out.println("  good style: " + x.displayName()));

        // ANTI: Optional<List<T>>
        // public Optional<List<Product>> findInStock(...) { ... }
        // PREFER returning an empty list — empty already means "no result".
        List<Product> stock = stockFor("widget");
        System.out.println("  stock count = " + stock.size() + " (empty list, no Optional wrapper)");
    }

    private static List<Product> stockFor(String query) {
        return CATALOG.stream().filter(p -> p.sku().contains(query)).toList();
    }

    /*
    Primitive streams

    IntStream, LongStream, and DoubleStream are specialized streams that keep elements unboxed. They exist for
    performance (no Integer boxing per element) and ergonomics (extra numeric ops on the stream).

    - Creation: IntStream.range(start, endExclusive), IntStream.rangeClosed(start, endInclusive),
      IntStream.of(...), Arrays.stream(int[]), someStream.mapToInt(toIntFn).
    - Terminal aggregation: sum, min, max, average, count. All eager.
    - Convert back to Stream<Integer> with .boxed() when you need Object-stream operations like collect.
    */
    static void primitiveStreams() {
        System.out.println("[Section 4] primitive streams");

        int sumRange = IntStream.rangeClosed(1, 100).sum();
        System.out.println("  sum 1..100 = " + sumRange);

        // mapToInt to extract a primitive metric, then aggregate
        double totalPrice = CATALOG.stream().mapToDouble(Product::price).sum();
        System.out.printf("  total catalog price = %.2f%n", totalPrice);

        long evens = IntStream.rangeClosed(1, 100).filter(n -> n % 2 == 0).count();
        System.out.println("  evens in 1..100 = " + evens);
    }

    /*
    IntSummaryStatistics

    stream.summaryStatistics() is a single-pass aggregator that computes count, sum, min, max, and average
    simultaneously. Cheaper than running each terminal op independently. Same family for Long and Double.

    The companion collector Collectors.summarizingInt(toIntFn) does the same on a regular Stream<T>.
    */
    static void summaryStatistics() {
        System.out.println("[Section 5] summary statistics");

        IntSummaryStatistics stats = CATALOG.stream()
                .mapToInt(p -> (int) Math.round(p.price()))
                .summaryStatistics();
        System.out.println("  prices summary = " + stats);
        System.out.printf("  count=%d, sum=%d, min=%d, max=%d, avg=%.2f%n",
                stats.getCount(), stats.getSum(), stats.getMin(), stats.getMax(), stats.getAverage());
    }

    /*
    Inter-conversion

    - IntStream::boxed        → Stream<Integer>. Useful before collect.
    - Stream<T>::mapToInt     → IntStream (also mapToLong, mapToDouble).
    - IntStream::mapToObj(fn) → Stream<R> without going through boxed.
    - IntStream::asLongStream, IntStream::asDoubleStream — widening, no boxing.

    Boxing has measurable cost only when the per-element work is small and N is large. For small streams, code
    clarity wins; reach for primitives only when profiling says so.
    */
    static void interConversion() {
        System.out.println("[Section 6] inter-conversion");

        // IntStream -> Stream<Integer> via boxed
        var boxed = IntStream.rangeClosed(1, 5).boxed().toList();
        System.out.println("  boxed list = " + boxed);

        // IntStream -> Stream<R> via mapToObj — no boxing-then-mapping detour
        var labels = IntStream.rangeClosed(1, 3).mapToObj(i -> "label-" + i).toList();
        System.out.println("  mapToObj labels = " + labels);

        // Widening conversion
        long widenedSum = IntStream.rangeClosed(1, 1000).asLongStream().sum();
        System.out.println("  asLongStream sum = " + widenedSum);

        // Stream<T> -> IntStream via mapToInt
        long words = Stream.of("alice", "bob", "carla").mapToInt(String::length).sum();
        System.out.println("  total chars = " + words);

        // LongStream summaries used in real code
        long max = LongStream.of(10L, 20L, 5L, 30L).max().orElseThrow();
        System.out.println("  LongStream max = " + max);
    }

    public static void main(String[] args) {
        optionalSemantics();
        optionalApi();
        optionalAntiPatterns();
        primitiveStreams();
        summaryStatistics();
        interConversion();
        System.out.println("Mod006OptionalAndPrimitiveStreams finished");
    }
}
