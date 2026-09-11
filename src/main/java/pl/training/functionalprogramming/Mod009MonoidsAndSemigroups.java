package pl.training.functionalprogramming;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/*
Monoids: the shape shared by every "combine these things" operation

This module names a pattern you have already written dozens of times without noticing, and then shows
what the name buys you.

Start with the observation. Summing numbers, multiplying them, concatenating strings, concatenating
lists, unioning sets, ANDing booleans, taking a maximum, merging two configuration maps - all of these
are "take two of these, produce one of these". And almost all of them share two properties:

    ASSOCIATIVITY   combine(a, combine(b, c)) == combine(combine(a, b), c)
                    It does not matter how you bracket the work.

    IDENTITY        there is some value `empty` with combine(empty, x) == x == combine(x, empty)
                    A neutral element that changes nothing.

A type plus an associative combine is called a SEMIGROUP. Add an identity and it is a MONOID. That is
the entire definition - two methods and two laws - and it takes about ten seconds to learn.

WHY IT IS WORTH LEARNING. Because those two laws are exactly the preconditions the JDK already demands
from you, in places where it does not explain why:

- Stream.reduce(identity, accumulator) requires the identity to be a genuine identity and the
  accumulator to be associative. The javadoc says so; this module says why.
- parallelStream().reduce(...) is only correct BECAUSE of those laws. Parallel reduction splits the
  input into chunks, seeds each chunk with the identity, reduces the chunks independently and combines
  the partial results. Associativity is what makes the arbitrary chunking safe; the identity is what
  makes seeding an empty chunk safe. Break either law and you get a result that is wrong only sometimes,
  only under load, and only on some machines - the worst class of bug there is.
- Collectors.reducing, Collector's own combiner, and every "merge two partial results" API in the JDK
  are asking for a monoid whether or not they use the word.

So the payoff is not vocabulary. It is a checklist: before you pass a lambda to reduce, ask whether it
is associative and whether your seed is really neutral. Subtraction is not associative. Averaging is not
associative. `(a, b) -> b` is associative but has no identity. Each of those is a real bug people ship.

The second payoff, in Section 5, is that monoids COMPOSE. Given monoids for A and B you get one for
Pair<A, B> for free, which means several independent aggregations collapse into a single pass over the
data - and that composition is completely mechanical.
*/

public final class Mod009MonoidsAndSemigroups {

    private Mod009MonoidsAndSemigroups() {}

    // =================================================================================================
    // Semigroup, Monoid
    // =================================================================================================

    /** A type with an associative combine. Associativity is a promise the compiler cannot check - it is
     *  on you, and Section 1 shows what goes wrong when the promise is false. */
    public interface Semigroup<A> {
        A combine(A a, A b);
    }

    /** A semigroup that also has a neutral element. */
    public interface Monoid<A> extends Semigroup<A> {
        A empty();
    }

    // ---- Stock monoids ----
    // Read each as a pair: which value changes nothing, and how two values merge.

    public static final Monoid<Long> SUM = new Monoid<>() {
        public Long empty() { return 0L; }                          // x + 0 == x
        public Long combine(Long a, Long b) { return a + b; }
    };
    public static final Monoid<Long> PRODUCT = new Monoid<>() {
        public Long empty() { return 1L; }                          // x * 1 == x  (NOT 0)
        public Long combine(Long a, Long b) { return a * b; }
    };
    public static final Monoid<Long> MAX = new Monoid<>() {
        public Long empty() { return Long.MIN_VALUE; }              // max(x, MIN_VALUE) == x
        public Long combine(Long a, Long b) { return Math.max(a, b); }
    };
    public static final Monoid<String> STRING = new Monoid<>() {
        public String empty() { return ""; }
        public String combine(String a, String b) { return a + b; }
    };
    public static final Monoid<Boolean> ALL = new Monoid<>() {
        public Boolean empty() { return true; }                     // true && x == x
        public Boolean combine(Boolean a, Boolean b) { return a && b; }
    };
    public static final Monoid<Boolean> ANY = new Monoid<>() {
        public Boolean empty() { return false; }                    // false || x == x
        public Boolean combine(Boolean a, Boolean b) { return a || b; }
    };
    public static final Monoid<BigDecimal> DECIMAL_SUM = new Monoid<>() {
        public BigDecimal empty() { return BigDecimal.ZERO; }
        public BigDecimal combine(BigDecimal a, BigDecimal b) { return a.add(b); }
    };

    /*
    MAX over BigDecimal is the interesting case, because it exposes the limit of the pattern.

    Long has a smallest value, so Long.MIN_VALUE is a genuine identity for max. BigDecimal is unbounded -
    there is no smallest BigDecimal - so a true identity for max simply DOES NOT EXIST. Max over
    BigDecimal is a lawful semigroup and not a lawful monoid.

    Two ways to deal with that, and it is worth seeing both:

    - DECIMAL_MAX_UNSAFE fakes it with a very negative sentinel. It works here only because the data
      happens to be non-negative amounts, and it is a latent bug: feed it a value below the sentinel and
      the identity law breaks silently. This is the kind of shortcut that survives review because the
      tests all use positive numbers.

    - OPTIONAL_MAX does it properly, using the standard trick: wrap the type in Optional and let
      Optional.empty() be the identity. Any semigroup can be turned into a monoid this way, because
      "nothing" is always a valid neutral element for "combine". The cost is a wrapper you have to unwrap
      at the end; the benefit is that the law actually holds for every input.

    Section 3 checks both against the identity law and shows the fake one failing.
    */
    public static final Monoid<BigDecimal> DECIMAL_MAX_UNSAFE = new Monoid<>() {
        public BigDecimal empty() { return new BigDecimal("-1E308"); }   // a sentinel, not an identity
        public BigDecimal combine(BigDecimal a, BigDecimal b) { return a.max(b); }
    };

    /** Lift any semigroup into a lawful monoid by adding "nothing" as the identity. */
    public static <A> Monoid<Optional<A>> optionalMonoid(Semigroup<A> sg) {
        return new Monoid<>() {
            public Optional<A> empty() { return Optional.empty(); }
            public Optional<A> combine(Optional<A> a, Optional<A> b) {
                if (a.isEmpty()) return b;
                if (b.isEmpty()) return a;
                return Optional.of(sg.combine(a.get(), b.get()));
            }
        };
    }
    public static final Monoid<Optional<BigDecimal>> OPTIONAL_DECIMAL_MAX =
            optionalMonoid((BigDecimal a, BigDecimal b) -> a.max(b));

    // ---- Generic helpers ----

    public static <A> Monoid<List<A>> listConcat() {
        return new Monoid<>() {
            public List<A> empty() { return List.of(); }
            public List<A> combine(List<A> a, List<A> b) {
                var out = new ArrayList<A>(a.size() + b.size()); out.addAll(a); out.addAll(b);
                return List.copyOf(out);
            }
        };
    }

    public static <A> Monoid<Set<A>> setUnion() {
        return new Monoid<>() {
            public Set<A> empty() { return Set.of(); }
            public Set<A> combine(Set<A> a, Set<A> b) {
                var out = new LinkedHashSet<A>(a); out.addAll(b);
                return Set.copyOf(out);
            }
        };
    }

    // ---- Composition: this is where monoids stop being trivia ----

    public record Pair<A, B>(A first, B second) {}

    /*
    Given a monoid for A and a monoid for B, you get a monoid for Pair<A, B> mechanically: the identity
    is the pair of identities, and combining is element-wise. Both laws survive, because each component
    obeys them independently.

    That is what makes multi-aggregation a single pass. Want count, total and max together? Compose three
    monoids into one and fold once, instead of traversing the data three times.
    */
    public static <A, B> Monoid<Pair<A, B>> pairMonoid(Monoid<A> ma, Monoid<B> mb) {
        return new Monoid<>() {
            public Pair<A, B> empty() { return new Pair<>(ma.empty(), mb.empty()); }
            public Pair<A, B> combine(Pair<A, B> x, Pair<A, B> y) {
                return new Pair<>(ma.combine(x.first(),  y.first()),
                                  mb.combine(x.second(), y.second()));
            }
        };
    }

    public record Triple<A, B, C>(A first, B second, C third) {}

    /** Same construction with three components. Nothing new happens - it scales as far as you need. */
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

    /*
    A Map<K, V> is a monoid whenever V is one: the identity is the empty map, and combining unions the
    key sets, merging the values of any key that appears on both sides using V's own combine.

    This single construction is a whole category of business logic: merging shopping carts, summing
    per-customer totals from two shards, layering configuration files, combining histograms. All of them
    are mapMonoid with a different V.
    */
    public static <K, V> Monoid<Map<K, V>> mapMonoid(Monoid<V> mv) {
        return new Monoid<>() {
            public Map<K, V> empty() { return Map.of(); }
            public Map<K, V> combine(Map<K, V> a, Map<K, V> b) {
                var out = new LinkedHashMap<K, V>(a);
                for (var e : b.entrySet()) {
                    out.merge(e.getKey(), e.getValue(), mv::combine);   // conflicts resolved by V's monoid
                }
                return Map.copyOf(out);
            }
        };
    }

    /*
    foldMap - map each element into the monoid, then fold everything together.

        foldMap(fn, M, xs) = xs.fold(M.empty(), (acc, x) -> M.combine(acc, fn(x)))

    Every aggregation you can express with a monoid is a foldMap. Note that it starts from M.empty(),
    which is why an empty input needs no special case: the answer is the identity.
    */
    public static <A, B> B foldMap(Function<A, B> fn, Monoid<B> m, List<A> xs) {
        B acc = m.empty();
        for (A x : xs) acc = m.combine(acc, fn.apply(x));
        return acc;
    }

    // =================================================================================================
    // Domain
    // =================================================================================================

    record Order(String id, BigDecimal amount, String customer) {}

    /** Test data. java.util.Random is impure, but it is seeded, so the generated list is identical on
     *  every run - which is all the determinism a fixture needs. Mod008 shows how to make the generator
     *  itself pure if you want the property rather than just the outcome. */
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
    Section 1 - Semigroup: one operation, one law

        A combine(A a, A b);    // must be associative

    Associativity means combine(a, combine(b, c)) == combine(combine(a, b), c). Note what it does NOT
    mean: it says nothing about ORDER. Associativity lets you re-bracket; commutativity would let you
    re-order, and most useful semigroups (string concat, list concat) are associative but not
    commutative.

    Re-bracketing is the property that matters in practice, because it is exactly what a parallel
    reduction does. Splitting a list into chunks, reducing each chunk, then combining the partial results
    is nothing more than a different bracketing of the same expression. If combine is associative, the
    answer is identical to the sequential one. If it is not, the answer depends on how the work happened
    to be split - which means it depends on the number of cores, the size of the input and the mood of
    the scheduler.

    The demo shows subtraction failing the law, because subtraction is the canonical trap: it looks like
    a perfectly good binary operation on numbers, and passing it to a parallel reduce gives you a
    plausible-looking wrong answer.
    */
    static void semigroupBasics() {
        System.out.println("[Section 1] Semigroup - associativity");

        long a = 2, b = 3, c = 5;
        System.out.println("  addition:    (2+3)+5 = " + ((a + b) + c)
                + ", 2+(3+5) = " + (a + (b + c)) + "  -> ASSOCIATIVE, safe to parallelise");
        System.out.println("  subtraction: (2-3)-5 = " + ((a - b) - c)
                + ", 2-(3-5) = " + (a - (b - c)) + " -> NOT ASSOCIATIVE, unsafe (this is the trap)");
        System.out.println("  concat:      (\"a\"+\"b\")+\"c\" = " + (("a" + "b") + "c")
                + ", \"a\"+(\"b\"+\"c\") = " + ("a" + ("b" + "c")) + "  -> ASSOCIATIVE (though not commutative)");
        System.out.println();
        System.out.println("  Pass subtraction to parallelStream().reduce(...) and the result depends on");
        System.out.println("  how the runtime happened to split the work. It will pass your unit test.");
    }

    /*
    Section 2 - Monoid: add an identity

        A empty();
        A combine(A a, A b);

    The identity law: combine(empty, x) == x == combine(x, empty).

      Type              empty              combine
      ----------------- ------------------ -----------------------
      Long (sum)        0L                 a + b
      Long (product)    1L                 a * b          <- 1, not 0
      Long (max)        Long.MIN_VALUE     Math.max(a, b)
      String            ""                 a + b
      List<A>           empty list         concatenation
      Set<A>            empty set          union
      Boolean AND       true               a && b         <- true, not false
      Boolean OR        false              a || b
      Map<K, V>         empty map          merge, combining values with V's monoid

    The two rows worth pausing on are product and AND, because the intuitive guess is wrong in both
    cases. The identity is not "the empty-looking value", it is "the value that changes nothing" - and
    for multiplication that is 1, for conjunction it is true. Getting this wrong turns every product into
    zero and every AND into false, which is a bug that shows up only when a collection is empty.
    */
    static void monoidStock() {
        System.out.println("[Section 2] stock monoids");
        System.out.println("  SUM:     empty = " + SUM.empty()     + ", combine(2,5) = " + SUM.combine(2L, 5L));
        System.out.println("  PRODUCT: empty = " + PRODUCT.empty() + ", combine(2,5) = " + PRODUCT.combine(2L, 5L));
        System.out.println("  MAX:     empty = " + MAX.empty()     + ", combine(7,3) = " + MAX.combine(7L, 3L));
        System.out.println("  STRING:  empty = \"" + STRING.empty() + "\", combine = " + STRING.combine("ab", "cd"));
        System.out.println("  ALL:     empty = " + ALL.empty()     + ", ANY: empty = " + ANY.empty());
        System.out.println("  setUnion: " + Mod009MonoidsAndSemigroups.<Integer>setUnion()
                .combine(Set.of(1, 2), Set.of(2, 3)));
    }

    /*
    Section 3 - why the identity matters, and when it does not exist

    Three concrete reasons the identity earns its place:

    - An empty input has a defined answer. foldMap over an empty list returns empty(), so "what is the
      sum of no numbers" needs no special case and no Optional. It is 0, and the type says so.
    - Parallel folds need it. Each chunk starts its accumulator at empty(); if that value were not
      neutral it would contaminate every chunk's partial result, and the contamination would scale with
      the number of chunks - so the answer would change with the core count.
    - Composed monoids need it. A pair-monoid seeds itself from the identities of both components, so
      neither component can opt out.

    And sometimes it genuinely does not exist. Max over BigDecimal has no smallest value, so no identity
    is possible. The demo below checks the identity law on both attempts: the sentinel-based one passes
    for ordinary data and is a lie in general, while the Optional-wrapped one is lawful for every input.

    When you hit this in real code, the Optional wrapper is the standard answer, and it is the reason
    Stream.max returns an Optional while Stream.sum does not.
    */
    static void identityMatters() {
        System.out.println("[Section 3] identity matters");

        long emptyCount = foldMap(x -> 1L, SUM, List.<Integer>of());
        System.out.println("  count of an empty list = " + emptyCount + " (the identity, no special case)");
        System.out.println("  product of empty list  = " + foldMap(Long::valueOf, PRODUCT, List.<Integer>of())
                + " (1, because 1 is what changes nothing)");

        // The identity law, tested on a value below the fake sentinel.
        var huge = new BigDecimal("-1E400");
        var viaFake = DECIMAL_MAX_UNSAFE.combine(DECIMAL_MAX_UNSAFE.empty(), huge);
        System.out.println("  sentinel identity, combine(empty, -1E400) gives back -1E400? "
                + (viaFake.compareTo(huge) == 0) + "  <- law BROKEN, as warned");

        var viaOptional = OPTIONAL_DECIMAL_MAX.combine(OPTIONAL_DECIMAL_MAX.empty(), Optional.of(huge));
        System.out.println("  Optional identity, combine(empty, -1E400) gives back -1E400? "
                + (viaOptional.orElseThrow().compareTo(huge) == 0) + "   <- law HOLDS for every input");
    }

    /*
    Section 4 - foldMap, and the JDK equivalents you already use

        foldMap(fn, M, xs) = start at M.empty(), and for each element combine in fn(element)

    Read it as "map each element to a monoid value, then fold them all together". Every monoid-based
    aggregation is a foldMap, which is why sum, count, max, concat and histogram below are all the same
    call with different arguments.

    The JDK spellings of the identical idea:

        xs.stream().reduce(M.empty(), (acc, x) -> M.combine(acc, fn(x)))
        xs.stream().map(fn).reduce(M.empty(), M::combine)
        xs.stream().collect(Collectors.reducing(M.empty(), fn, M::combine))

    and, crucially, the three-argument reduce used by parallel streams:

        xs.parallelStream().reduce(M.empty(), (acc, x) -> M.combine(acc, fn(x)), M::combine)

    That last overload asks you for the identity and the combiner explicitly. Now you know what it is
    really asking for: a monoid. Its javadoc requires associativity and a genuine identity, and it cannot
    check either - it trusts you, and silently produces wrong answers if you are wrong.
    */
    static void foldMapDemo() {
        System.out.println("[Section 4] foldMap");
        var nums = List.of(1, 2, 3, 4, 5);

        System.out.println("  sum     = " + foldMap(Long::valueOf, SUM, nums));
        System.out.println("  product = " + foldMap(Long::valueOf, PRODUCT, nums));
        System.out.println("  count   = " + foldMap(x -> 1L, SUM, nums) + "   (map everything to 1, then sum)");
        System.out.println("  max     = " + foldMap(Long::valueOf, MAX, nums));
        System.out.println("  allEven = " + foldMap(x -> x % 2 == 0, ALL, nums));
        System.out.println("  anyEven = " + foldMap(x -> x % 2 == 0, ANY, nums));

        // The identical computation through the JDK, to make the correspondence concrete.
        long viaJdk = nums.stream().map(Long::valueOf).reduce(SUM.empty(), SUM::combine);
        System.out.println("  the same sum via Stream.reduce(identity, combine) = " + viaJdk);
    }

    /*
    Section 5 - composition: pair-monoids and map-monoids

    Monoids compose, mechanically:

    - Pair<A, B> is a monoid whenever A and B are. Identity = (emptyA, emptyB), combine is element-wise.
      Both laws hold automatically, because each half obeys them on its own.
    - Map<K, V> is a monoid whenever V is. Combine unions the keys, and resolves any key present on both
      sides with V's combine.

    This is what makes several aggregates cost one pass instead of N. Every time you have written three
    loops over the same collection to get a count, a total and a maximum, you were paying for the absence
    of this composition.

    The histogram in the demo is worth a second look, because it is a genuinely useful trick in two
    lines: map each element to a single-entry map {element: 1}, then fold with mapMonoid(SUM). The
    monoid does all the counting - there is no get-or-default, no merge callback, no mutable map.
    */
    static void pairAndMapMonoids() {
        System.out.println("[Section 5] pair and map monoids");

        var sumAndProduct = pairMonoid(SUM, PRODUCT);
        var nums = List.of(1, 2, 3, 4);
        var pair = foldMap(n -> new Pair<>((long) n, (long) n), sumAndProduct, nums);
        System.out.println("  one pass over [1,2,3,4] -> (sum, product) = " + pair);

        var hist = foldMap(c -> Map.<Character, Long>of(c, 1L),
                mapMonoid(SUM),
                List.of('a', 'b', 'a', 'c', 'b', 'a'));
        System.out.println("  letter histogram = " + hist);
        System.out.println("  ^ each letter became {letter: 1}, and the map-monoid summed the collisions");
    }

    /*
    Section 6 - end-to-end: three aggregates over 10000 orders in one pass

    Compute count, total and maximum amount over 10000 orders using a single triple-monoid fold, and
    check the result against independent reference computations.

    The FP version maps each order to a Triple(1, amount, amount) - "this order contributes one to the
    count, its amount to the total, and its amount as a max candidate" - and folds once. The composed
    monoid does the rest, and adding a fourth aggregate would mean widening the tuple, not adding a
    fourth traversal.

    Because every component is associative with a genuine identity, this fold is also correct in
    parallel, unchanged. That is the practical cash value of the two laws: it is the difference between
    an aggregation you can hand to parallelStream and one you cannot.
    */
    static void endToEnd() {
        System.out.println("[Section 6] end-to-end - multi-aggregate over 10k orders");

        var orders = generateOrders(10_000);

        // One pass, three aggregates, via a composed monoid.
        var aggregateMonoid = tripleMonoid(SUM, DECIMAL_SUM, OPTIONAL_DECIMAL_MAX);
        Triple<Long, BigDecimal, Optional<BigDecimal>> fp = foldMap(
                o -> new Triple<>(1L, o.amount(), Optional.of(o.amount())),
                aggregateMonoid,
                orders);

        // Reference: three independent traversals.
        long refCount = orders.size();
        BigDecimal refTotal = orders.stream().map(Order::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refMax   = orders.stream().map(Order::amount).max(BigDecimal::compareTo).orElseThrow();

        boolean ok = fp.first() == refCount
                && fp.second().compareTo(refTotal) == 0
                && fp.third().orElseThrow().compareTo(refMax) == 0;

        System.out.println("  one pass  = count " + fp.first() + ", total " + fp.second()
                + ", max " + fp.third().orElseThrow());
        System.out.println("  reference = count " + refCount + ", total " + refTotal + ", max " + refMax);
        System.out.println("  match? " + ok + (ok ? " ✓" : " ✗"));
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
}
