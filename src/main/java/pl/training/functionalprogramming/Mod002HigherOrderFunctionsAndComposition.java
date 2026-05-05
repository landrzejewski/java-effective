package pl.training.functionalprogramming;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Mod002HigherOrderFunctionsAndComposition {

    private Mod002HigherOrderFunctionsAndComposition() {}

    // --- Section 2: HOFs reused throughout ---
    static <A, B> List<B> transform(List<A> xs, Function<A, B> fn) {
        return xs.stream().map(fn).toList();
    }
    static <A> List<A> select(List<A> xs, Predicate<A> p) {
        return xs.stream().filter(p).toList();
    }

    // --- Section 3: curry / uncurry ---
    static <A, B, R> Function<A, Function<B, R>> curry(BiFunction<A, B, R> f) {
        return a -> b -> f.apply(a, b);
    }
    static <A, B, R> BiFunction<A, B, R> uncurry(Function<A, Function<B, R>> f) {
        return (a, b) -> f.apply(a).apply(b);
    }

    /*
    First-class functions

    A first-class function can be:

    - assigned to a variable,
    - passed as an argument,
    - returned from another function,
    - stored in a data structure.

    In Java, every functional interface is first-class — a Function<T, R>, Predicate<T>, BiFunction<T, U, R> is just
    a value. Lambdas and method references make first-class functions cheap to write.

    This module focuses on what you can do with that capability: pass behaviour, build behaviour from smaller pieces,
    configure functions from data.
    */
    static void firstClassFunctions() {
        System.out.println("[Section 1] first-class functions");
        List<Function<Integer, Integer>> ops = List.of(x -> x + 1, x -> x * 2, x -> x * x);
        for (var op : ops) System.out.println("  op.apply(5) = " + op.apply(5));
    }

    /*
    Higher-order functions

    A higher-order function (HOF) takes a function as a parameter, returns a function, or both.

    - transform(list, fn) — takes a transformation as a parameter.
    - select(list, predicate) — same idea for filtering.
    - whenMatched(predicate, handler) — returns a function configured by inputs.
    - The JDK's Stream API is built entirely on HOFs (map, filter, reduce).

    HOFs let one piece of code drive many behaviours; the what and the how can move independently.
    */
    static void higherOrderFunctions() {
        System.out.println("[Section 2] higher-order functions");
        var nums = List.of(1, 2, 3, 4, 5);
        System.out.println("  transform(*2)   = " + transform(nums, n -> n * 2));
        System.out.println("  select(>2)      = " + select(nums, n -> n > 2));
    }

    /*
    Currying

    Currying converts an N-argument function into N nested 1-argument functions:

    BiFunction<A, B, R>           ↔   Function<A, Function<B, R>>
    TriFunction<A, B, C, R>       ↔   Function<A, Function<B, Function<C, R>>>

    Curried functions compose more easily because every step is a Function<X, Y> — the standard interface that all
    the JDK combinators work with. They also make partial application natural (next section).

    The cost: more allocations and more nested types in error messages. Curry on demand, do not write everything in
    curried form.
    */
    static void curryingDemo() {
        System.out.println("[Section 3] currying");
        BiFunction<Integer, Integer, Integer> add = Integer::sum;
        Function<Integer, Function<Integer, Integer>> curriedAdd = curry(add);
        System.out.println("  curriedAdd.apply(2).apply(3) = " + curriedAdd.apply(2).apply(3));
        System.out.println("  uncurry(curry(add)).apply(2,3) = " + uncurry(curriedAdd).apply(2, 3));
    }

    /*
    Partial application

    Partial application fixes some arguments of a multi-argument function, producing a function that takes the
    remaining ones:

    BiFunction<Integer, Integer, Integer> add = Integer::sum;
    Function<Integer, Integer> addFive = b -> add.apply(5, b);   // partial

    The currying form makes partial application a one-step operation: addFive = curry(add).apply(5).

    Use cases: build configuration-style helpers (logAt(LEVEL_ERROR, msg)), specialise generic operations
    (replaceAll(map, "x" -> _)), adapt one functional interface to another.
    */
    static void partialApplicationDemo() {
        System.out.println("[Section 4] partial application");
        BiFunction<Integer, Integer, Integer> add = Integer::sum;
        Function<Integer, Integer> addFive = b -> add.apply(5, b);
        System.out.println("  addFive(3)  = " + addFive.apply(3));

        // Configuration-style helper: a logger pre-configured to a level.
        BiFunction<String, String, String> log = (level, msg) -> "[" + level + "] " + msg;
        Function<String, String> warn = msg -> log.apply("WARN", msg);
        System.out.println("  warn        = " + warn.apply("disk almost full"));
    }

    /*
    Composition (andThen / compose / identity)

    Function provides three combinators:

    - f.andThen(g)  — left-to-right; (f.andThen(g)).apply(x) = g(f(x)).
    - f.compose(g)  — right-to-left; (f.compose(g)).apply(x) = f(g(x)).
    - Function.identity() — x -> x. The neutral element for composition; useful when an API requires a Function but
      you have nothing to do.

    Compose pure functions into pipelines that read in the order in which data flows. The composed function is
    itself a value — store it, log it, test it.
    */
    static void compositionDemo() {
        System.out.println("[Section 5] composition");
        Function<Integer, Integer> plus3   = x -> x + 3;
        Function<Integer, Integer> times2  = x -> x * 2;
        System.out.println("  plus3.andThen(times2).apply(5) = " + plus3.andThen(times2).apply(5)); // (5+3)*2=16
        System.out.println("  plus3.compose(times2).apply(5) = " + plus3.compose(times2).apply(5)); // (5*2)+3=13

        // identity as an unbiased default
        Function<Integer, Integer> noop = Function.identity();
        System.out.println("  identity.apply(7) = " + noop.apply(7));
    }

    // --- Section 6: ETL pipeline ---
    record Raw(String name, String numericString) {}
    record Scored(String name, int score, String tier) {}

    static String tierOf(int score) {
        if (score >= 80) return "A";
        if (score >= 50) return "B";
        return "C";
    }

    static List<Scored> composedPipeline(List<Raw> raws) {
        Function<Raw, Raw>     trim    = r -> new Raw(r.name().strip(), r.numericString().strip());
        Function<Raw, Scored>  parse   = r -> new Scored(r.name(), Integer.parseInt(r.numericString()), null);
        Function<Scored, Scored> score = s -> new Scored(s.name(), s.score(), tierOf(s.score()));

        Function<Raw, Scored> pipeline = trim.andThen(parse).andThen(score);
        return transform(raws, pipeline);
    }

    static List<Scored> imperativePipeline(List<Raw> raws) {
        var out = new java.util.ArrayList<Scored>();
        for (var r : raws) {
            String name = r.name().strip();
            String numStr = r.numericString().strip();
            int score = Integer.parseInt(numStr);
            String tier = tierOf(score);
            out.add(new Scored(name, score, tier));
        }
        return List.copyOf(out);
    }

    /*
    End-to-end ETL pipeline

    Build the same "extract / clean / score / classify" pipeline two ways:

    1. As a chain of composed Functions.
    2. As an imperative for-loop with intermediate variables.

    The two outputs are compared element-by-element to confirm the composed pipeline matches the imperative
    reference.
    */
    static void endToEnd() {
        System.out.println("[Section 6] end-to-end ETL self-check");

        var raws = List.of(
                new Raw("alice ",  " 92"),
                new Raw(" bob",    "55"),
                new Raw("carla",   " 30 "),
                new Raw("dave",    "73"));

        var fp        = composedPipeline(raws);
        var reference = imperativePipeline(raws);

        boolean ok = fp.equals(reference);
        System.out.println("  composed = " + fp);
        System.out.println("  imperative reference = " + reference);
        System.out.println("  composed equals imperative? " + ok);
    }

    public static void main(String[] args) {
        firstClassFunctions();
        higherOrderFunctions();
        curryingDemo();
        partialApplicationDemo();
        compositionDemo();
        endToEnd();
        System.out.println("Mod002HigherOrderFunctionsAndComposition finished");
    }
}
