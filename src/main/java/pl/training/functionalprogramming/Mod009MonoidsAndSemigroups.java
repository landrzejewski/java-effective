package pl.training.functionalprogramming;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public final class Mod009MonoidsAndSemigroups {

    private Mod009MonoidsAndSemigroups() {}

    // =================================================================================================
    // Semigroup, Monoid
    // =================================================================================================

    public interface Semigroup<A> {
        A combine(A a, A b);
    }

    public interface Monoid<A> extends Semigroup<A> {
        A empty();
    }

    // Stock monoids
    public static final Monoid<Long> SUM = new Monoid<>() {
        public Long empty() { return 0L; }
        public Long combine(Long a, Long b) { return a + b; }
    };
    public static final Monoid<Long> PRODUCT = new Monoid<>() {
        public Long empty() { return 1L; }
        public Long combine(Long a, Long b) { return a * b; }
    };
    public static final Monoid<Long> MAX = new Monoid<>() {
        public Long empty() { return Long.MIN_VALUE; }
        public Long combine(Long a, Long b) { return Math.max(a, b); }
    };
    public static final Monoid<String> STRING = new Monoid<>() {
        public String empty() { return ""; }
        public String combine(String a, String b) { return a + b; }
    };
    public static final Monoid<BigDecimal> DECIMAL_SUM = new Monoid<>() {
        public BigDecimal empty() { return BigDecimal.ZERO; }
        public BigDecimal combine(BigDecimal a, BigDecimal b) { return a.add(b); }
    };
    public static final Monoid<BigDecimal> DECIMAL_MAX = new Monoid<>() {
        public BigDecimal empty() { return new BigDecimal("-1E308"); }
        public BigDecimal combine(BigDecimal a, BigDecimal b) { return a.max(b); }
    };

    // Generic helpers
    public static <A> Monoid<List<A>> listConcat() {
        return new Monoid<>() {
            public List<A> empty() { return List.of(); }
            public List<A> combine(List<A> a, List<A> b) {
                var out = new ArrayList<A>(a.size() + b.size()); out.addAll(a); out.addAll(b);
                return List.copyOf(out);
            }
        };
    }

    // Pair-monoid
    public record Pair<A, B>(A first, B second) {}

    public static <A, B> Monoid<Pair<A, B>> pairMonoid(Monoid<A> ma, Monoid<B> mb) {
        return new Monoid<>() {
            public Pair<A, B> empty() { return new Pair<>(ma.empty(), mb.empty()); }
            public Pair<A, B> combine(Pair<A, B> x, Pair<A, B> y) {
                return new Pair<>(ma.combine(x.first(), y.first()),
                                  mb.combine(x.second(), y.second()));
            }
        };
    }

    // Triple-monoid (built from pair)
    public record Triple<A, B, C>(A first, B second, C third) {}
    public static <A, B, C> Monoid<Triple<A, B, C>> tripleMonoid(Monoid<A> ma, Monoid<B> mb, Monoid<C> mc) {
        return new Monoid<>() {
            public Triple<A, B, C> empty() { return new Triple<>(ma.empty(), mb.empty(), mc.empty()); }
            public Triple<A, B, C> combine(Triple<A, B, C> x, Triple<A, B, C> y) {
                return new Triple<>(
                        ma.combine(x.first(),  y.first()),
                        mb.combine(x.second(), y.second()),
                        mc.combine(x.third(),  y.third()));
            }
        };
    }

    // Map-monoid: keys union, values combined when both sides have a key
    public static <K, V> Monoid<Map<K, V>> mapMonoid(Monoid<V> mv) {
        return new Monoid<>() {
            public Map<K, V> empty() { return Map.of(); }
            public Map<K, V> combine(Map<K, V> a, Map<K, V> b) {
                var out = new LinkedHashMap<K, V>(a);
                for (var e : b.entrySet()) {
                    out.merge(e.getKey(), e.getValue(), mv::combine);
                }
                return Map.copyOf(out);
            }
        };
    }

    // foldMap
    public static <A, B> B foldMap(Function<A, B> fn, Monoid<B> m, List<A> xs) {
        B acc = m.empty();
        for (A x : xs) acc = m.combine(acc, fn.apply(x));
        return acc;
    }

    // =================================================================================================
    // Domain
    // =================================================================================================

    record Order(String id, BigDecimal amount, String customer) {}

    static List<Order> generateOrders(int n) {
        var rnd = new java.util.Random(7);
        var customers = List.of("alice", "bob", "carla", "dave", "eve");
        var out = new ArrayList<Order>(n);
        for (int i = 0; i < n; i++) {
            var amount = BigDecimal.valueOf(rnd.nextInt(100_000)).movePointLeft(2);
            out.add(new Order("o-" + i, amount, customers.get(rnd.nextInt(customers.size()))));
        }
        return List.copyOf(out);
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    /*
    Semigroup<A>

    A Semigroup<A> is a type with one operation:

    A combine(A a, A b);    // associative

    Associativity means combine(a, combine(b, c)) == combine(combine(a, b), c). That is the only law.

    Why care? Associativity lets you fold a list left-to-right or right-to-left or in chunks (parallel) and get the
    same answer. Most "summing" operations in business code are semigroups: max, min, string concat, list concat,
    set union, sum, product.
    */
    static void semigroupBasics() {
        System.out.println("[Section 1] Semigroup");
        // Associativity check on +
        long a = 2, b = 3, c = 5;
        System.out.println("  (2+3)+5 == 2+(3+5)? " + ((a + b) + c == a + (b + c)));
    }

    /*
    Monoid<A>

    Monoid<A> is a Semigroup<A> with an identity element:

    A empty();
    A combine(A a, A b);

    Identity means combine(empty, x) == x == combine(x, empty).

      Type             empty                   combine
      ---------------- ----------------------- ------------------------
      Long (sum)       0L                      a + b
      Long (product)   1L                      a * b
      String           ""                      a + b
      List<A>          empty list              concatenation
      Set<A>           empty set               union
      Boolean AND      true                    a && b
      Boolean OR       false                   a || b
      Long (max)       Long.MIN_VALUE          Math.max(a, b)
    */
    static void monoidStock() {
        System.out.println("[Section 2] stock monoids");
        System.out.println("  SUM.empty   = " + SUM.empty());
        System.out.println("  PRODUCT     = " + PRODUCT.combine(2L, 5L));
        System.out.println("  MAX of 7,3  = " + MAX.combine(7L, 3L));
        System.out.println("  STRING      = " + STRING.combine("ab", "cd"));
    }

    /*
    Why the identity matters

    - Empty input has a defined answer. Folding an empty list returns empty(). Without an identity you would have
      to handle the empty case separately.
    - Parallel folds need the identity. Each chunk seeds its accumulator with empty() and combines on the way up;
      the identity ensures the initial accumulator does not change the result.
    - Composing folds. A monoid (A, A') of pairs needs identities of both components to seed itself.
    */
    static void identityMatters() {
        System.out.println("[Section 3] identity matters");
        long emptySum = foldMap(x -> 1L, SUM, List.<Integer>of());
        System.out.println("  foldMap over empty list (count) = " + emptySum + " (uses identity)");
    }

    /*
    foldMap

    foldMap(fn, monoid, items) is the canonical monoid-fold:

    foldMap(fn, M, xs) = xs.fold(M.empty, (acc, x) -> M.combine(acc, fn(x)))

    Read it as "map each element to a monoid value, then fold them all together". Every aggregation built on top of
    a monoid is a foldMap.
    */
    static void foldMapDemo() {
        System.out.println("[Section 4] foldMap");
        var nums = List.of(1, 2, 3, 4, 5);
        long sum  = foldMap(Long::valueOf, SUM, nums);
        long prod = foldMap(Long::valueOf, PRODUCT, nums);
        System.out.println("  sum  = " + sum + ", product = " + prod);
    }

    /*
    Composition — pair-monoid and map-monoid

    Monoids compose:

    - A pair (A, B) is a monoid when A and B are. Identity is (emptyA, emptyB); combine is element-wise.
    - A Map<K, V> is a monoid when V is. Combine merges keys; for conflicts, combine the values with V's monoid.

    This composition is what lets you compute several aggregates in one pass — the topic of §6.
    */
    static void pairAndMapMonoids() {
        System.out.println("[Section 5] pair and map monoids");
        var sumAndProduct = pairMonoid(SUM, PRODUCT);
        var nums = List.of(1, 2, 3, 4);
        var result = foldMap(n -> new Pair<>((long) n, (long) n), sumAndProduct, nums);
        System.out.println("  pair-fold (sum, product) = " + result);

        var hist = foldMap(c -> Map.<Character, Long>of(c, 1L),
                mapMonoid(SUM),
                List.of('a', 'b', 'a', 'c', 'b', 'a'));
        System.out.println("  letter histogram          = " + hist);
    }

    /*
    End-to-end — multi-aggregate over 10k orders

    Aggregate (count, totalAmount, maxAmount) over 10 000 orders in a single fold using a tuple-monoid. Compare
    against three independent imperative loops.
    */
    static void endToEnd() {
        System.out.println("[Section 6] end-to-end — multi-aggregate over 10k orders");

        var orders = generateOrders(10_000);

        // FP version: one pass with a triple-monoid (count, total, max).
        var aggregateMonoid = tripleMonoid(SUM, DECIMAL_SUM, DECIMAL_MAX);
        Triple<Long, BigDecimal, BigDecimal> fp = foldMap(
                o -> new Triple<>(1L, o.amount(), o.amount()),
                aggregateMonoid,
                orders);

        // Imperative reference: three independent loops.
        long refCount = orders.size();
        BigDecimal refTotal = orders.stream().map(Order::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refMax   = orders.stream().map(Order::amount).max(BigDecimal::compareTo).orElseThrow();

        boolean ok = fp.first() == refCount
                && fp.second().compareTo(refTotal) == 0
                && fp.third().compareTo(refMax)   == 0;

        System.out.println("  FP triple = " + fp);
        System.out.println("  reference = (" + refCount + ", " + refTotal + ", " + refMax + ")");
        System.out.println("  match? " + ok);
    }

    public static void main(String[] args) {
        semigroupBasics();
        monoidStock();
        identityMatters();
        foldMapDemo();
        pairAndMapMonoids();
        endToEnd();
        System.out.println("Mod009MonoidsAndSemigroups finished");
    }

    @SuppressWarnings("unused")
    private static <T> Stream<T> never() { return Stream.empty(); }
}
