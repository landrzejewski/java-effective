package pl.training.functionalprogramming;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import pl.training.functionalprogramming.Mod005OptionEitherTry.Option;
import pl.training.functionalprogramming.Mod005OptionEitherTry.Some;

/*
Laziness: describing a computation without performing it

Java evaluates arguments before it calls anything. Write f(g(x)) and g(x) runs first, always, even if f
turns out not to need it. That is "strict" (or "eager") evaluation, and it is such a deep default that
most Java developers have never had to name it.

Laziness is the opposite: pass around a recipe for the value instead of the value, and run the recipe
only when somebody actually needs the answer. In Java the recipe is a Supplier<A> - usually called a
*thunk* - and that single idea produces three capabilities you cannot otherwise have:

- Infinite structures. "All the natural numbers" is a perfectly good value if you never compute more of
  it than you look at.
- Fused pipelines. map followed by filter followed by take does not build three intermediate lists; it
  pulls elements through the whole chain one at a time.
- Work avoided entirely. take(10) after an expensive filter runs the filter until ten elements have got
  through, and then stops - not once per element in the source.

This module rebuilds a lazy Stream from scratch, because the mechanism is only ten lines and seeing it
removes all the mystery. Compared with java.util.stream.Stream:

- Same pull model. Intermediate operations on the JDK Stream also produce nothing until a terminal
  operation asks.
- The JDK Stream is single-shot: after one terminal operation the source is consumed and reusing the
  stream throws. Ours can be traversed repeatedly, because the thunks are still sitting there.
- Ours cannot run in parallel. The JDK gets that from the ForkJoin common pool.
- Ours does not MEMOISE, and the JDK does not either. Section 5 is about why that matters and what real
  lazy-list implementations do differently.
*/

public final class Mod007LazyStreams {

    private Mod007LazyStreams() {}

    // =================================================================================================
    // Stream<A>
    //
    // Compare with Mod004's FList: same two shapes (empty / head-and-tail), one change.
    //
    //   FList:   Cons(A head,           FList<A> tail)             both already computed
    //   Stream:  Cons(Supplier<A> head, Supplier<Stream<A>> tail)  neither computed until asked
    //
    // Wrapping the tail in a Supplier is what makes infinite streams possible: a value cannot contain
    // itself forever, but a function that would produce the rest, if called, is just an object.
    // =================================================================================================

    public sealed interface Stream<A> permits Empty, Cons {

        @SuppressWarnings("unchecked")
        static <A> Stream<A> empty() { return (Stream<A>) Empty.INSTANCE; }

        /* Note honestly what this does and does not do: the varargs array already holds fully evaluated
           values, so `of` does not defer computing 1, 2 and 3 - they exist before the call. What is lazy
           here is the STRUCTURE: the cons cells are only unwrapped as you walk them. For genuinely
           deferred elements, build with iterate or unfold instead. */
        @SafeVarargs
        static <A> Stream<A> of(A... xs) {
            Stream<A> acc = empty();
            for (int i = xs.length - 1; i >= 0; i--) {
                final A v = xs[i];
                final Stream<A> tail = acc;
                acc = new Cons<>(() -> v, () -> tail);
            }
            return acc;
        }

        /* An infinite stream in two lines. There is no recursion problem here even though iterate calls
           itself: the recursive call is inside a lambda, so it does not happen until something pulls the
           tail. Building this value performs exactly zero applications of f. */
        static <A> Stream<A> iterate(A seed, UnaryOperator<A> f) {
            return new Cons<>(() -> seed, () -> iterate(f.apply(seed), f));
        }

        /* unfold is the general generator, and the dual of a fold: where a fold consumes a structure
           down to one value, unfold grows a structure up from one value. The step function receives the
           current state and returns either Some((element, nextState)) to continue, or None to stop. Any
           stream you can describe with a state machine, you can build with unfold. */
        static <S, A> Stream<A> unfold(S seed, Function<S, Option<Pair<A, S>>> step) {
            var stepped = step.apply(seed);
            if (stepped instanceof Some<Pair<A, S>>(Pair<A, S> pair)) {
                return new Cons<>(() -> pair.first(), () -> unfold(pair.second(), step));
            }
            return empty();
        }

        /* take is where laziness pays. It does not evaluate anything - it returns a new Cons whose head
           thunk is the ORIGINAL head thunk (still unevaluated) and whose tail thunk, when eventually
           pulled, will take(n-1) of the rest. This is what makes take(10) of an infinite stream finite. */
        default Stream<A> take(int n) {
            if (n <= 0) return empty();
            return switch (this) {
                case Empty<A> _ -> empty();
                case Cons<A>(Supplier<A> h, Supplier<Stream<A>> t) ->
                        new Cons<>(h, () -> t.get().take(n - 1));
            };
        }

        /* takeWhile MUST evaluate each head to test it - there is no way to know whether to stop
           without looking. But it still only evaluates as far as the first failing element. */
        default Stream<A> takeWhile(Predicate<A> p) {
            return switch (this) {
                case Empty<A> _ -> empty();
                case Cons<A>(Supplier<A> h, Supplier<Stream<A>> t) -> {
                    A v = h.get();
                    yield p.test(v)
                            ? new Cons<>(() -> v, () -> t.get().takeWhile(p))
                            : empty();
                }
            };
        }

        /* map evaluates nothing. It wraps the existing head thunk in a new thunk that will apply f
           later. Chain ten maps and you have ten nested thunks and zero calls to any of the functions. */
        default <B> Stream<B> map(Function<A, B> f) {
            return switch (this) {
                case Empty<A> _ -> empty();
                case Cons<A>(Supplier<A> h, Supplier<Stream<A>> t) ->
                        new Cons<>(() -> f.apply(h.get()), () -> t.get().map(f));
            };
        }

        /* filter has to evaluate heads to test them, and it keeps going until it finds one that passes.
           On an infinite stream with a predicate nothing satisfies, this loops forever - the one way to
           hang a lazy stream. Laziness bounds how much work a *terminating* pipeline does; it cannot
           make a search for something that does not exist terminate. */
        default Stream<A> filter(Predicate<A> p) {
            return switch (this) {
                case Empty<A> _ -> empty();
                case Cons<A>(Supplier<A> h, Supplier<Stream<A>> t) -> {
                    A v = h.get();
                    yield p.test(v)
                            ? new Cons<>(() -> v, () -> t.get().filter(p))
                            : t.get().filter(p);
                }
            };
        }

        /* zip stops as soon as either side runs out, which is what lets you pair a finite stream with an
           infinite one - the classic "number these lines" idiom. */
        default <B> Stream<Pair<A, B>> zip(Stream<B> other) {
            if (this instanceof Cons<A>(Supplier<A> h1, Supplier<Stream<A>> t1)
                    && other instanceof Cons<B>(Supplier<B> h2, Supplier<Stream<B>> t2)) {
                return new Cons<>(() -> new Pair<>(h1.get(), h2.get()),
                        () -> t1.get().zip(t2.get()));
            }
            return empty();
        }

        /* toList is the terminal operation: the only method here that forces anything. Every thunk in
           the chain runs now, on demand, one element at a time. Calling this on an infinite stream never
           returns - always put a take or takeWhile in front of it. */
        default List<A> toList() {
            var out = new ArrayList<A>();
            Stream<A> here = this;
            while (here instanceof Cons<A>(Supplier<A> h, Supplier<Stream<A>> t)) {
                out.add(h.get());
                here = t.get();
            }
            return List.copyOf(out);
        }
    }

    public record Empty<A>() implements Stream<A> {
        static final Empty<?> INSTANCE = new Empty<>();
    }
    public record Cons<A>(Supplier<A> head, Supplier<Stream<A>> tail) implements Stream<A> {}

    public record Pair<A, B>(A first, B second) {}

    // =================================================================================================
    // Sections
    // =================================================================================================

    /*
    Section 1 - lazy vs strict

    - A strict function evaluates its arguments before the call. That is Java's default everywhere except
      &&, ||, and the ternary operator, which are the language's three built-in lazy constructs.
    - A lazy function receives a thunk - a Supplier<A> - instead of a value, and calls .get() only if it
      needs it. Nothing about this requires language support; a Supplier is an ordinary object.

    The demo builds a Cons whose head thunk prints when it runs. Notice the order of the output: the
    "(computing 42)" line appears only when we ask for the head, which is one line later than where the
    thunk was created. Building the structure ran nothing.

    That gap - between describing a computation and performing it - is the entire subject of this module,
    and it comes back in Mod010 with effects instead of values.
    */
    static void lazyVsStrict() {
        System.out.println("[Section 1] lazy vs strict");
        Supplier<Integer> thunk = () -> { System.out.println("    (computing 42 - the thunk is running now)"); return 42; };

        var s = new Cons<>(thunk, () -> Stream.<Integer>empty());
        System.out.println("  Cons created. Nothing above this line computed 42.");
        int head = s.head().get();                      // forcing happens here
        System.out.println("  pulled head = " + head);
    }

    /*
    Section 2 - the cons-cell Stream

        sealed interface Stream<A> permits Empty, Cons {}
        record Empty<A>()                                          implements Stream<A> {}
        record Cons<A>(Supplier<A> head, Supplier<Stream<A>> tail)  implements Stream<A> {}

    One character of difference from Mod004's list, conceptually: both fields are Suppliers.

    The tail being a Supplier is the important one. A record cannot contain an infinite chain of records,
    but it can perfectly well contain a function that would produce the next link if called. The
    infinity is potential, not actual - it exists only as far as you walk it.

    Be precise about what Stream.of(1, 2, 3) does, since it is easy to overclaim: the numbers 1, 2 and 3
    are ordinary arguments and already exist by the time `of` is entered. What is deferred is the
    structure, not those values. Genuinely deferred elements come from iterate and unfold, where each
    element is produced by a function that has not been called yet.
    */
    static void streamShape() {
        System.out.println("[Section 2] cons-cell Stream");
        var s = Stream.of(1, 2, 3);
        System.out.println("  Stream.of(1,2,3).toList() = " + s.toList());
        System.out.println("  (the values already existed; the cons cells are what unfold lazily)");
    }

    /*
    Section 3 - combinators, and how work is actually avoided

    All of these return immediately without computing elements:

    - take(n)       first n elements; nothing beyond them is ever touched.
    - takeWhile(p)  elements while p holds; evaluates one element past the last kept one, to know to stop.
    - map(f)        a transformed view; each element's thunk gets wrapped in another thunk.
    - filter(p)     a filtered view; must evaluate heads to test them, but no further than needed.
    - zip(other)    pairs, ending when either side does.

    The demo below makes the work-avoidance measurable. It maps a *counting* function over six elements
    and then takes two. A strict list would call the function six times. Here it is called twice, because
    only two elements are ever pulled through - and the count is printed to prove it.

    This is the same fusion that makes JDK streams efficient: source.map(f).filter(p).findFirst() does not
    map the whole collection, it pulls single elements through the whole chain until one comes out.
    */
    static void combinators() {
        System.out.println("[Section 3] combinators");
        var s = Stream.of(1, 2, 3, 4, 5, 6);
        System.out.println("  take(3)        = " + s.take(3).toList());
        System.out.println("  takeWhile(<4)  = " + s.takeWhile(x -> x < 4).toList());
        System.out.println("  map(*2)        = " + s.map(x -> x * 2).toList());
        System.out.println("  filter(odd)    = " + s.filter(x -> x % 2 != 0).toList());

        var letters = Stream.of("a", "b", "c");
        var nums    = Stream.of(1, 2, 3);
        System.out.println("  zip            = " + letters.zip(nums).toList());

        // How much work does map actually do? Count the calls.
        var calls = new int[1];
        var mapped = Stream.of(1, 2, 3, 4, 5, 6).map(x -> { calls[0]++; return x * 10; });
        System.out.println("  after building map over 6 elements, f ran " + calls[0] + " times");
        var firstTwo = mapped.take(2).toList();
        System.out.println("  after take(2).toList() = " + firstTwo + ", f ran " + calls[0] + " times");
        System.out.println("  ^ 6 elements in the source, 2 needed, 2 computed. Nothing was wasted.");
    }

    /*
    Section 4 - infinite streams

    - iterate(seed, f) produces seed, f(seed), f(f(seed)), ... forever.
    - unfold(seed, step) is the general form: step(state) returns Some((element, nextState)) to keep
      going, or None to stop. iterate is unfold with a step that never returns None.

    An infinite stream is a perfectly ordinary value. You can pass it to a method, store it in a field,
    and it occupies a few dozen bytes, because none of it exists yet. What you must not do is call a
    terminal operation without bounding it first: toList on an infinite stream runs until you kill the
    process.

    The safe combinators to put in front are take(n) and takeWhile(p). Note that filter is NOT one of
    them - filter(x -> false) over an infinite stream will search forever for an element that passes.
    Laziness limits how much work a terminating program does; it does not make a hopeless search finish.
    */
    static void infiniteStreams() {
        System.out.println("[Section 4] infinite streams");
        var naturals = Stream.iterate(1, n -> n + 1);
        System.out.println("  built an infinite stream of naturals - cost so far: one Cons object");
        System.out.println("  naturals.take(10)          = " + naturals.take(10).toList());
        System.out.println("  naturals.map(*3).take(5)   = " + naturals.map(n -> n * 3).take(5).toList());
        System.out.println("  naturals.takeWhile(< 6)    = " + naturals.takeWhile(n -> n < 6).toList());

        // Numbering lines: a finite stream zipped against an infinite one.
        var lines = Stream.of("alpha", "beta", "gamma");
        System.out.println("  zip with naturals          = " + lines.zip(naturals).toList());
    }

    /*
    Section 5 - the caveat: these thunks are not memoised

    This is the part that is easy to miss and expensive to discover in production.

    Nothing in this implementation caches a thunk's result. Every time you traverse the stream, every
    head thunk runs again. Traverse a mapped stream twice and f runs twice per element. Worse, iterate's
    tail thunk recomputes f.apply(seed) on every pull, so the cost of reaching element n is paid again
    from scratch on each traversal.

    The demo makes this concrete by traversing the same mapped stream twice and counting the calls: the
    second traversal doubles the count, doing no less work than the first.

    Real lazy-list implementations - Scala's LazyList, Haskell's lists, Clojure's lazy-seq - memoise:
    each cell evaluates its thunk at most once and caches the result, so the first traversal is lazy and
    every later one is a cheap walk over already-computed values. The fix here is mechanical: wrap each
    Supplier in a caching Supplier that computes once and stores the answer. (Doing it properly under
    concurrency is where it stops being a one-liner.)

    java.util.stream.Stream sidesteps the question by refusing to be traversed twice at all - a second
    terminal operation throws IllegalStateException. That is a defensible choice: without memoisation,
    re-traversal is a performance trap, so the JDK makes it an error rather than a silent cost.

    Practical rule for the stream in this file: traverse once. If you need the elements twice, call
    toList() and use that.
    */
    static void memoisationCaveat() {
        System.out.println("[Section 5] thunks are NOT memoised");

        var calls = new int[1];
        var mapped = Stream.of(1, 2, 3).map(x -> { calls[0]++; return x * 2; });

        System.out.println("  first  toList() = " + mapped.toList() + ", f has run " + calls[0] + " times");
        System.out.println("  second toList() = " + mapped.toList() + ", f has run " + calls[0] + " times");
        System.out.println("  ^ the same work, done twice. Scala's LazyList would report 3 both times.");
        System.out.println("  (java.util.stream.Stream avoids the trap by throwing on the second pass)");
    }

    static boolean isPrime(int n) {
        if (n < 2) return false;
        for (int d = 2; (long) d * d <= n; d++) if (n % d == 0) return false;
        return true;
    }

    /*
    Section 6 - end-to-end: the first 10 primes and the first 12 Fibonacci numbers

    Both are classic demonstrations because both are naturally infinite and neither has a useful "how
    many do I need to generate" answer up front.

    - Primes: start from an infinite stream of integers, filter by trial division, take 10. Note what is
      NOT in that description - any decision about how far to search. The filter runs exactly as far as
      producing ten survivors requires, and then stops, because take stopped pulling.
    - Fibonacci via unfold: the state is the pair (current, next). Each step emits `current` and moves
      the state to (next, current + next). BigInteger is used because Fibonacci outgrows long quickly and
      this is the sort of stream you would want to keep pulling from.

    Both results are compared against hard-coded reference values, so the check would catch an off-by-one
    in take, a mis-ordered unfold state, or a filter that skipped an element.
    */
    static void endToEnd() {
        System.out.println("[Section 6] end-to-end self-checks");

        var primes = Stream.iterate(2, n -> n + 1)
                .filter(Mod007LazyStreams::isPrime)
                .take(10)
                .toList();
        var primeReference = List.of(2, 3, 5, 7, 11, 13, 17, 19, 23, 29);
        boolean primesOk = primes.equals(primeReference);
        System.out.println("  first 10 primes    = " + primes + (primesOk ? " ✓" : " ✗"));

        // Fibonacci via unfold. State = (current, next); emit current, advance to (next, current+next).
        var fibs = Stream.<Pair<BigInteger, BigInteger>, BigInteger>unfold(
                        new Pair<>(BigInteger.ZERO, BigInteger.ONE),
                        s -> Option.some(
                                new Pair<>(s.first(), new Pair<>(s.second(), s.first().add(s.second())))))
                .take(12)
                .toList();
        var fibsReference = List.of(0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89).stream()
                .map(BigInteger::valueOf).toList();
        boolean fibsOk = fibs.equals(fibsReference);
        System.out.println("  first 12 Fibonacci = " + fibs + (fibsOk ? " ✓" : " ✗"));

        System.out.println("  all self-checks pass? " + (primesOk && fibsOk));
    }

    public static void main(String[] args) {
        lazyVsStrict();
        streamShape();
        combinators();
        infiniteStreams();
        memoisationCaveat();
        endToEnd();
        System.out.println("Mod007LazyStreams finished");
    }
}
