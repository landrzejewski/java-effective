package pl.training.functionalfeatures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntBinaryOperator;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;

public final class Mod002BuiltInFunctionalInterfaces {

    private Mod002BuiltInFunctionalInterfaces() {}

    record Customer(String name, String email, int age) {}

    private static final List<Customer> CUSTOMERS = List.of(
            new Customer("Alice", "alice@example.com", 28),
            new Customer("",      "blank@example.com", 99),
            new Customer("Bob",   "BOB@EXAMPLE.COM",   17),
            new Customer("Carla", "carla@example.com", 35));

    /*
    Predicate<T>

    - The shape T -> boolean. Used to filter, partition, validate, and short-circuit.
    - Combinators built into the interface:
      - p.and(q) — short-circuit AND. The right-hand side is not evaluated if p is false.
      - p.or(q)  — short-circuit OR.
      - p.negate() — complement.
    - Static helpers:
      - Predicate.isEqual(x) — t -> x.equals(t). Null-safe on the left side.
      - Predicate.not(p) — same as p.negate() but reads better in stream pipelines:
        .filter(Predicate.not(String::isBlank)).
    */
    static void predicateCombinators() {
        System.out.println("[Section 1] Predicate combinators");

        Predicate<Customer> hasName  = c -> !c.name().isBlank();
        Predicate<Customer> isAdult  = c -> c.age() >= 18;
        Predicate<Customer> isEligible = hasName.and(isAdult);

        var eligible = CUSTOMERS.stream().filter(isEligible).toList();
        System.out.println("  hasName.and(isAdult) -> " + eligible);

        // Predicate.not reads more naturally than negate() inline.
        var named = CUSTOMERS.stream().filter(Predicate.not(c -> c.name().isBlank())).toList();
        System.out.println("  Predicate.not(blank) -> " + named.size() + " kept");
    }

    /*
    Function<T,R> and UnaryOperator<T>

    - Function<T,R> shape: T -> R. The general transformation type.
    - UnaryOperator<T> extends Function<T,T> — same input and output type. Use it whenever the transformation
      preserves the type (e.g., String -> String for trimming and casing).
    - Combinators:
      - f.andThen(g) — first f, then g. Reads left-to-right.
      - f.compose(g) — first g, then f. Reads right-to-left, i.e. f.compose(g).apply(x) == f.apply(g.apply(x)).
    - Function.identity() is the do-nothing function. Useful as a default parameter or when a Function is
      structurally required but no transformation is wanted.
    */
    static void functionAndUnaryOperator() {
        System.out.println("[Section 2] Function / UnaryOperator");

        UnaryOperator<String> trim     = String::strip;
        UnaryOperator<String> lower    = String::toLowerCase;
        UnaryOperator<String> normalize = s -> trim.andThen(lower).apply(s);

        Function<Customer, String> emailKey = c -> normalize.apply(c.email());
        var keys = CUSTOMERS.stream().map(emailKey).toList();
        System.out.println("  normalized emails: " + keys);

        // andThen vs compose
        Function<Integer, Integer> plus3  = x -> x + 3;
        Function<Integer, Integer> times2 = x -> x * 2;
        System.out.println("  plus3.andThen(times2).apply(5) = " + plus3.andThen(times2).apply(5)); // (5+3)*2 = 16
        System.out.println("  plus3.compose(times2).apply(5) = " + plus3.compose(times2).apply(5)); // (5*2)+3 = 13
    }

    /*
    Consumer<T> and BiConsumer<T,U>

    - Side-effecting types: T -> void, (T, U) -> void. They produce no value.
    - Used by forEach (collections, streams, Map.forEach).
    - andThen chains two consumers — both run, in order. Useful for telemetry + business logic combinations.
    */
    static void consumerCombinators() {
        System.out.println("[Section 3] Consumer / BiConsumer");

        var sink = new ArrayList<String>();
        Consumer<Customer> log     = c -> System.out.println("  log: " + c.name());
        Consumer<Customer> collect = c -> sink.add(c.name());
        Consumer<Customer> both    = log.andThen(collect);

        CUSTOMERS.forEach(both);
        System.out.println("  collected names: " + sink);

        BiConsumer<String, Integer> mapEntry = (k, v) -> System.out.println("    " + k + " -> " + v);
        Map.of("Alice", 28, "Bob", 17).forEach(mapEntry);
    }

    /*
    Supplier<T>

    - Shape () -> T. A thunk. Most useful when the value should be produced on demand, not eagerly:
      - Optional.orElseGet(supplier) — only invoked if the optional is empty.
      - Logger.log(level, supplier) — only invoked if the level is enabled.
      - Stream.generate(supplier) — infinite generator.
    - Pairs with BooleanSupplier, IntSupplier, LongSupplier, DoubleSupplier for primitive returns without boxing.
    */
    static void supplierLaziness() {
        System.out.println("[Section 4] Supplier — laziness");

        Supplier<String> expensive = () -> {
            System.out.println("  (expensive call ran)");
            return "computed";
        };

        // orElse runs the supplier eagerly; orElseGet defers it.
        Optional<String> present = Optional.of("alice");
        String e = present.orElse(expensive.get());           // eager: runs supplier, throws away result
        String l = present.orElseGet(expensive);              // lazy:  doesn't run supplier — present is present
        System.out.println("  orElse value:    " + e);
        System.out.println("  orElseGet value: " + l);
    }

    /*
    Bi-argument variants

    - BiPredicate<T,U> — (T,U) -> boolean.
    - BiFunction<T,U,R> — (T,U) -> R. Returns a value.
    - BinaryOperator<T> extends BiFunction<T,T,T> — same type for both inputs and output. Used for reductions and
      "merge" callbacks (Map.merge, Stream.reduce).
    - Static helpers on BinaryOperator: minBy(comparator), maxBy(comparator).
    - For arities greater than two, you need a custom interface (Mod001 §5) or curry/partially apply:
      (a, b) -> (c) -> ....
    */
    static void biVariants() {
        System.out.println("[Section 5] Bi-argument variants");

        BiPredicate<String, Integer> longerThan = (s, n) -> s.length() > n;
        System.out.println("  'Alice' longerThan 4? " + longerThan.test("Alice", 4));

        BiFunction<Integer, Integer, Integer> sum = Integer::sum;
        BinaryOperator<Integer> minBy = BinaryOperator.minBy(Integer::compareTo);
        System.out.println("  sum(2,3) = " + sum.apply(2, 3));
        System.out.println("  minBy(7,3) = " + minBy.apply(7, 3));

        // BinaryOperator used as a Map.merge function — sums hits per name.
        var hits = new java.util.HashMap<String, Integer>();
        for (var c : CUSTOMERS) hits.merge(c.name(), 1, Integer::sum);
        System.out.println("  hits per name: " + hits);
    }

    /*
    Primitive specializations

    - For each primitive type (int, long, double, boolean), java.util.function provides "specialized" interfaces
      that take or return the primitive unboxed:
      - IntPredicate, IntFunction<R>, IntUnaryOperator, IntBinaryOperator.
      - ToIntFunction<T>, IntToLongFunction, IntToDoubleFunction.
    - Why bother? Each int boxed into Integer is an allocation. In tight pipelines (millions of elements), that
      overhead dominates. Specialized interfaces and the parallel IntStream/LongStream/DoubleStream keep things on
      the stack.
    - Rule of thumb: use the boxed Function<T,R> family for ergonomic code with small N; switch to the primitive
      variants when profiling shows boxing on the hot path.
    */
    static void primitiveSpecializations() {
        System.out.println("[Section 6] Primitive specializations");

        IntPredicate isAdult = age -> age >= 18;
        IntUnaryOperator nextYear = age -> age + 1;
        IntBinaryOperator sum = Integer::sum;
        IntFunction<String> bracket = age -> age < 18 ? "minor" : age < 65 ? "adult" : "senior";
        ToIntFunction<Customer> ageOf = Customer::age;

        int[] ages = CUSTOMERS.stream().mapToInt(ageOf).toArray();
        int total = Arrays.stream(ages).reduce(0, sum);

        System.out.println("  ages              = " + Arrays.toString(ages));
        System.out.println("  next year ages    = " + Arrays.toString(Arrays.stream(ages).map(nextYear).toArray()));
        System.out.println("  adults            = " + Arrays.stream(ages).filter(isAdult).count());
        System.out.println("  age brackets      = " + Arrays.stream(ages).mapToObj(bracket).toList());
        System.out.println("  total years lived = " + total);
    }

    public static void main(String[] args) {
        predicateCombinators();
        functionAndUnaryOperator();
        consumerCombinators();
        supplierLaziness();
        biVariants();
        primitiveSpecializations();
        System.out.println("Mod002BuiltInFunctionalInterfaces finished");
    }
}
