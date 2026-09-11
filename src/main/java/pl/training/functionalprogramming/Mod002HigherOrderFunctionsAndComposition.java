package pl.training.functionalprogramming;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/*
Functions as values, and what you can build once you have them

Java has been able to "pass behaviour to a method" since anonymous inner classes, but lambdas made it
cheap enough to actually do. This module is about what becomes possible once a function is just another
value you can store, pass, return and combine.

Four techniques, each building on the last:

- Higher-order functions - a method that takes or returns a function. This is the mechanism; everything
  else here is a use of it.
- Currying - rewriting a two-argument function as a one-argument function that returns a one-argument
  function. Sounds pointless until you see partial application.
- Partial application - fixing some arguments now and supplying the rest later, producing a specialised
  function from a general one.
- Composition - gluing small functions into a pipeline with andThen / compose, so the pipeline itself
  becomes a value you can name, store, pass around and test.

The payoff at the end is an ETL pipeline built as a composed Function and checked against the equivalent
for-loop. The two produce identical output; the difference is what you can do with the result afterwards.
*/

public final class Mod002HigherOrderFunctionsAndComposition {

    private Mod002HigherOrderFunctionsAndComposition() {}

    // --- Section 2: two higher-order functions reused throughout the module ---
    // Both take a function as a parameter, which is what makes them "higher-order".
    static <A, B> List<B> transform(List<A> xs, Function<A, B> fn) {
        return xs.stream().map(fn).toList();
    }
    static <A> List<A> select(List<A> xs, Predicate<A> p) {
        return xs.stream().filter(p).toList();
    }

    // --- Section 3: curry / uncurry ---
    // curry: (A, B) -> R      becomes   A -> (B -> R)
    // uncurry undoes it. Note how tiny both are: currying changes the shape, never the behaviour.
    static <A, B, R> Function<A, Function<B, R>> curry(BiFunction<A, B, R> f) {
        return a -> b -> f.apply(a, b);
    }
    static <A, B, R> BiFunction<A, B, R> uncurry(Function<A, Function<B, R>> f) {
        return (a, b) -> f.apply(a).apply(b);
    }

    /*
    Section 1 - first-class functions

    A value is "first-class" in a language when you can do all the ordinary value things with it:

    - assign it to a variable,
    - pass it as an argument,
    - return it from a method,
    - store it in a data structure.

    In Java, any functional interface instance qualifies. A Function<T, R>, a Predicate<T>, a
    BiFunction<T, U, R> is just an object, and a lambda or method reference is a cheap way to make one.
    Nothing exotic is happening - x -> x + 1 compiles to an object with one method.

    The demo below does the fourth item, which is the one people forget: a List<Function<Integer,
    Integer>>. Once behaviour lives in a collection you can iterate it, filter it, or look one up by key
    - which is how a switch over strategy names becomes a Map<String, Function<...>>.
    */
    static void firstClassFunctions() {
        System.out.println("[Section 1] first-class functions");
        // Three different behaviours, stored in an ordinary List like any other data.
        List<Function<Integer, Integer>> ops = List.of(x -> x + 1, x -> x * 2, x -> x * x);
        for (var op : ops) System.out.println("  op.apply(5) = " + op.apply(5));
    }

    /*
    Section 2 - higher-order functions

    A higher-order function (HOF) takes a function as a parameter, returns a function, or both. That is
    the whole definition.

    - transform(list, fn) takes the transformation as a parameter, so one implementation of "walk the
      list and build a new one" serves every possible element mapping.
    - select(list, predicate) does the same for filtering.
    - curry (above) *returns* a function, which is the other half of the definition.
    - The entire Stream API is HOFs: map, filter, reduce, sorted(Comparator), collect(Collector).

    The value is separating the *what* from the *how*. transform owns the traversal, the allocation and
    the result-building; the caller owns the element logic. Either can change without touching the other.
    Compare against writing the loop each time, where the traversal is copy-pasted next to every new
    piece of element logic and the two are welded together.
    */
    static void higherOrderFunctions() {
        System.out.println("[Section 2] higher-order functions");
        var nums = List.of(1, 2, 3, 4, 5);
        System.out.println("  transform(*2)   = " + transform(nums, n -> n * 2));
        System.out.println("  select(>2)      = " + select(nums, n -> n > 2));
    }

    /*
    Section 3 - currying

    Currying converts an N-argument function into N nested one-argument functions:

        BiFunction<A, B, R>          becomes   Function<A, Function<B, R>>
        TriFunction<A, B, C, R>      becomes   Function<A, Function<B, Function<C, R>>>

    Reading the curried type out loud helps: "give me an A, and I will hand you back something that
    wants a B and then produces an R".

    Why bother, when Java already has BiFunction? Two reasons:

    - Uniformity. After currying, *every* function is a Function<X, Y>. That is the one interface all the
      JDK combinators speak (andThen, compose, Optional.map, Stream.map), so a curried function plugs
      into them; a BiFunction does not.
    - Partial application becomes a single call - curry(add).apply(5) - instead of a hand-written wrapper
      lambda. That is Section 4.

    The cost is real: one extra object per nesting level, and genuinely awful type names in compiler
    errors. Curry when it buys you something, not as a default style.

    curry and uncurry are exact inverses, which the demo checks: uncurry(curry(add)) behaves like add.
    */
    static void curryingDemo() {
        System.out.println("[Section 3] currying");
        BiFunction<Integer, Integer, Integer> add = Integer::sum;

        // One argument at a time. curriedAdd.apply(2) adds nothing yet - it returns a function that is
        // still waiting for the second number.
        Function<Integer, Function<Integer, Integer>> curriedAdd = curry(add);
        Function<Integer, Integer> addTwo = curriedAdd.apply(2);   // nothing computed yet
        System.out.println("  curriedAdd.apply(2) returned a function, not a number");
        System.out.println("  ...then .apply(3)              = " + addTwo.apply(3));

        // Round-trip: uncurry undoes curry, so this must behave exactly like add.
        System.out.println("  uncurry(curry(add)).apply(2,3) = " + uncurry(curriedAdd).apply(2, 3));
    }

    /*
    Section 4 - partial application

    Partial application means fixing some arguments of a multi-argument function now, and getting back a
    function that still wants the rest:

        BiFunction<Integer, Integer, Integer> add = Integer::sum;
        Function<Integer, Integer> addFive = b -> add.apply(5, b);   // 5 is baked in

    With the curried form it is not even a lambda, just a call: addFive = curry(add).apply(5).

    This is how you turn one general function into a family of specialised ones without writing an
    overload for each. Typical uses:

    - configuration-style helpers - fix the log level once, get warn(msg) and error(msg),
    - specialising a generic operation - fix the separator, the charset, the rounding mode,
    - adapting one functional interface to another - a BiFunction where the API wants a Function.

    The specialised function is a value, so it can live in a field, be passed to a constructor, or be put
    in a map. A Java *method* can do none of those things.
    */
    static void partialApplicationDemo() {
        System.out.println("[Section 4] partial application");
        BiFunction<Integer, Integer, Integer> add = Integer::sum;

        // Two spellings of the same idea. The second is what currying buys you.
        Function<Integer, Integer> addFiveByLambda = b -> add.apply(5, b);
        Function<Integer, Integer> addFiveByCurry  = curry(add).apply(5);
        System.out.println("  addFive(3) via lambda = " + addFiveByLambda.apply(3));
        System.out.println("  addFive(3) via curry  = " + addFiveByCurry.apply(3));

        // Configuration-style helper: one general logger, several pre-configured views of it.
        BiFunction<String, String, String> log = (level, msg) -> "[" + level + "] " + msg;
        Function<String, String> warn  = curry(log).apply("WARN");
        Function<String, String> error = curry(log).apply("ERROR");
        System.out.println("  warn  = " + warn.apply("disk almost full"));
        System.out.println("  error = " + error.apply("disk full"));
    }

    /*
    Section 5 - composition: andThen, compose, identity

    Function ships with three combinators, and the only hard part is remembering which way each one runs:

    - f.andThen(g) - left to right. f runs first: f.andThen(g).apply(x) == g(f(x)).
    - f.compose(g) - right to left. g runs first: f.compose(g).apply(x) == f(g(x)). This is the
      mathematical convention, and it reads backwards compared to the way data actually flows, which is
      why andThen is usually the better choice in application code.
    - Function.identity() - just x -> x. It is the neutral element for composition: identity.andThen(f)
      and f.andThen(identity) both behave like f. Useful whenever an API demands a Function and you have
      nothing to do - a default in a lookup, the "no transformation" branch of a conditional.

    The demo runs the same two functions both ways so the difference in the result (16 vs 13) makes the
    ordering concrete.

    The important part is not the syntax. It is that plus3.andThen(times2) is a *value* - a Function you
    can assign to a field, pass to a method, return from a factory, or unit-test on its own. A pipeline
    written as a chain of statements inside a method body is none of those things.

    Function.identity() being the neutral element is not a coincidence: functions under composition form
    a monoid, which is Mod009's topic. Composition turns out to be the same pattern as string
    concatenation and list concatenation, just with functions as the elements.
    */
    static void compositionDemo() {
        System.out.println("[Section 5] composition");
        Function<Integer, Integer> plus3   = x -> x + 3;
        Function<Integer, Integer> times2  = x -> x * 2;

        // andThen: plus3 first.   (5 + 3) * 2 = 16
        System.out.println("  plus3.andThen(times2).apply(5) = " + plus3.andThen(times2).apply(5));
        // compose: times2 first.  (5 * 2) + 3 = 13
        System.out.println("  plus3.compose(times2).apply(5) = " + plus3.compose(times2).apply(5));

        // identity is the neutral element: composing with it changes nothing.
        Function<Integer, Integer> noop = Function.identity();
        System.out.println("  identity.apply(7)              = " + noop.apply(7));
        System.out.println("  plus3.andThen(identity)(7)     = " + plus3.andThen(noop).apply(7)
                + "  (same as plus3 alone)");
    }

    // --- Section 6: a small ETL pipeline, built twice ---
    record Raw(String name, String numericString) {}
    record Scored(String name, int score, String tier) {}

    static String tierOf(int score) {
        if (score >= 80) return "A";
        if (score >= 50) return "B";
        return "C";
    }

    // Each stage is a standalone Function, independently testable, then glued together.
    // The types line up like dominoes: Raw -> Raw -> Scored -> Scored.
    static List<Scored> composedPipeline(List<Raw> raws) {
        Function<Raw, Raw>       trim  = r -> new Raw(r.name().strip(), r.numericString().strip());
        Function<Raw, Scored>    parse = r -> new Scored(r.name(), Integer.parseInt(r.numericString()), null);
        Function<Scored, Scored> score = s -> new Scored(s.name(), s.score(), tierOf(s.score()));

        Function<Raw, Scored> pipeline = trim.andThen(parse).andThen(score);
        return transform(raws, pipeline);
    }

    // The same logic as a loop. Correct, and at four lines arguably clearer - but every intermediate
    // step is a local variable, so none of them can be reused, replaced or tested on its own.
    static List<Scored> imperativePipeline(List<Raw> raws) {
        var out = new ArrayList<Scored>();
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
    Section 6 - end-to-end ETL pipeline

    The same "clean / parse / score / classify" job written twice: as a chain of composed Functions, and
    as an imperative for-loop with intermediate variables. The self-check compares the outputs, which
    must be equal - composition does not change *what* is computed.

    What it changes is what you can do afterwards. In the composed version each stage is a named value,
    so you can test parse without running trim, swap score for a different rule at runtime, or assemble
    the pipeline from configuration. In the loop version each stage is a local variable that exists for
    one line and cannot be reached from outside.

    One thing neither version handles: Integer.parseInt throws on garbage input - exactly the "throwing
    for ordinary conditions" problem from Mod001 Section 2. A malformed row does not produce a bad
    result, it destroys the whole batch, and neither version says so in its type. Mod005 fixes this by
    giving parse the return type Try<Integer>, so failure becomes a value the pipeline carries rather
    than an exception that escapes it.
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
        System.out.println("  composed             = " + fp);
        System.out.println("  imperative reference = " + reference);
        System.out.println("  composed equals imperative? " + ok + (ok ? " ✓" : " ✗"));
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
