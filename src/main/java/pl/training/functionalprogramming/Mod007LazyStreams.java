package pl.training.functionalprogramming;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

// =================================================================================================
// Section 1: Lazy vs strict
// =================================================================================================

/*
## Lazy vs strict

- A *strict* function evaluates its arguments before the call. Java's
  default — `f(g(x))` always runs `g(x)` first.
- A *lazy* function defers evaluation: instead of a value, it receives a
  thunk (`Supplier`) that produces the value on demand.
- Laziness lets you describe **infinite** structures and pipeline them:
  the consumer pulls only as many elements as it needs.
- This module rebuilds Stream from scratch with cons-cell laziness so the
  mechanism is visible. The JDK's `java.util.stream.Stream` works with
  the same pull model but materialises one-shot.
*/

// =================================================================================================
// Section 2: A cons-cell Stream
// =================================================================================================

/*
## A cons-cell Stream

```
sealed interface Stream<A> permits Empty, Cons {}
record Empty<A>()                                  implements Stream<A> {}
record Cons<A>(Supplier<A> head, Supplier<Stream<A>> tail) implements Stream<A> {}
```

The crucial detail: `head` and `tail` are *thunks*, not eager values.
A `Stream.of(1, 2, 3)` does not evaluate `1`, `2`, or `3` until something
asks for them.
*/

// =================================================================================================
// Section 3: Combinators
// =================================================================================================

/*
## Combinators

All preserve laziness:

- `take(n)`              — first n elements; nothing computed beyond them.
- `takeWhile(p)`         — elements while the predicate holds.
- `map(f)`, `filter(p)`  — transformed view; new thunks wrap the originals.
- `zip(other)`           — pairs of corresponding elements; ends when
                           either side runs out.
*/

// =================================================================================================
// Section 4: Infinite streams
// =================================================================================================

/*
## Infinite streams

- `iterate(seed, fn)` — `seed, fn(seed), fn(fn(seed)), ...`.
- `unfold(seed, step)` — generic generator: `step(state)` returns
  `Some((value, nextState))` or `None` to terminate.

Without a `take` upstream of any terminal op, an infinite stream loops
forever. Lazy combinators safely compose with `take`/`takeWhile`.
*/

// =================================================================================================
// Section 5: How this differs from java.util.stream.Stream
// =================================================================================================

/*
## How this differs from java.util.stream.Stream

- The JDK Stream uses the **same pull model** (intermediate ops do not
  produce values until a terminal op pulls).
- The JDK Stream is **single-shot**: after one terminal op the source is
  consumed. Our cons-cell Stream is **re-iterable** because the thunks
  are still in place.
- Our Stream lacks parallel execution; the JDK Stream gets that via the
  ForkJoin common pool.
*/

// =================================================================================================
// Section 6: End-to-end — first 10 primes + first 12 Fibonacci
// =================================================================================================

/*
## End-to-end — first 10 primes + first 12 Fibonacci

- Build an infinite stream of natural numbers, filter out non-primes
  (trial division), `take(10)` the result.
- Build a Fibonacci stream by `unfold`-ing a `(prev, curr)` pair,
  `take(12)`.
- Compare both lists against an independent reference computation.
*/

public final class Mod007LazyStreams {

    private Mod007LazyStreams() {}

    // =================================================================================================
    // Stream<A>
    // =================================================================================================

    public sealed interface Stream<A> permits Empty, Cons {

        @SuppressWarnings("unchecked")
        static <A> Stream<A> empty() { return (Stream<A>) Empty.INSTANCE; }

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

        static <A> Stream<A> iterate(A seed, UnaryOperator<A> f) {
            return new Cons<>(() -> seed, () -> iterate(f.apply(seed), f));
        }

        static <S, A> Stream<A> unfold(S seed, Function<S, Mod005OptionEitherTry.Option<Pair<A, S>>> step) {
            var stepped = step.apply(seed);
            if (stepped instanceof Mod005OptionEitherTry.Some<Pair<A, S>>(Pair<A, S> pair)) {
                return new Cons<>(() -> pair.first(), () -> unfold(pair.second(), step));
            }
            return empty();
        }

        default Stream<A> take(int n) {
            if (n <= 0) return empty();
            return switch (this) {
                case Empty<A> __ -> empty();
                case Cons<A>(Supplier<A> h, Supplier<Stream<A>> t) ->
                        new Cons<>(h, () -> t.get().take(n - 1));
            };
        }

        default Stream<A> takeWhile(Predicate<A> p) {
            return switch (this) {
                case Empty<A> __ -> empty();
                case Cons<A>(Supplier<A> h, Supplier<Stream<A>> t) -> {
                    A v = h.get();
                    yield p.test(v)
                            ? new Cons<>(() -> v, () -> t.get().takeWhile(p))
                            : empty();
                }
            };
        }

        default <B> Stream<B> map(Function<A, B> f) {
            return switch (this) {
                case Empty<A> __ -> empty();
                case Cons<A>(Supplier<A> h, Supplier<Stream<A>> t) ->
                        new Cons<>(() -> f.apply(h.get()), () -> t.get().map(f));
            };
        }

        default Stream<A> filter(Predicate<A> p) {
            return switch (this) {
                case Empty<A> __ -> empty();
                case Cons<A>(Supplier<A> h, Supplier<Stream<A>> t) -> {
                    A v = h.get();
                    yield p.test(v)
                            ? new Cons<>(() -> v, () -> t.get().filter(p))
                            : t.get().filter(p);
                }
            };
        }

        default <B> Stream<Pair<A, B>> zip(Stream<B> other) {
            if (this instanceof Cons<A>(Supplier<A> h1, Supplier<Stream<A>> t1)
                    && other instanceof Cons<B>(Supplier<B> h2, Supplier<Stream<B>> t2)) {
                return new Cons<>(() -> new Pair<>(h1.get(), h2.get()),
                        () -> t1.get().zip(t2.get()));
            }
            return empty();
        }

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

    static void lazyVsStrict() {
        System.out.println("[Section 1] lazy vs strict");
        Supplier<Integer> thunk = () -> { System.out.println("    (computing 42)"); return 42; };
        var s = new Cons<>(thunk, () -> Stream.<Integer>empty());
        System.out.println("  Cons created — thunk has not run yet");
        System.out.println("  pulling head: " + s.head().get());
    }

    static void streamShape() {
        System.out.println("[Section 2] cons-cell Stream");
        var s = Stream.of(1, 2, 3);
        System.out.println("  Stream.of(1,2,3).toList() = " + s.toList());
    }

    static void combinators() {
        System.out.println("[Section 3] combinators");
        var s = Stream.of(1, 2, 3, 4, 5, 6);
        System.out.println("  take(3)        = " + s.take(3).toList());
        System.out.println("  takeWhile(<4)  = " + s.takeWhile(x -> x < 4).toList());
        System.out.println("  map(*2)        = " + s.map(x -> x * 2).toList());
        System.out.println("  filter(odd)    = " + s.filter(x -> x % 2 != 0).toList());

        var letters = Stream.of("a", "b", "c");
        var nums    = Stream.of(1, 2, 3);
        System.out.println("  zip(letters, nums) = " + letters.zip(nums).toList());
    }

    static void infiniteStreams() {
        System.out.println("[Section 4] infinite streams");
        var naturals = Stream.iterate(1, n -> n + 1);
        System.out.println("  naturals.take(10) = " + naturals.take(10).toList());
    }

    static boolean isPrime(int n) {
        if (n < 2) return false;
        for (int d = 2; (long) d * d <= n; d++) if (n % d == 0) return false;
        return true;
    }

    static void endToEnd() {
        System.out.println("[Section 6] end-to-end self-checks");

        var primes = Stream.iterate(2, n -> n + 1)
                .filter(Mod007LazyStreams::isPrime)
                .take(10)
                .toList();
        var primeReference = List.of(2, 3, 5, 7, 11, 13, 17, 19, 23, 29);
        boolean primesOk = primes.equals(primeReference);
        System.out.println("  first 10 primes      = " + primes + (primesOk ? " ✓" : " ✗"));

        // Fibonacci via unfold
        var fibs = Stream.<Pair<BigInteger, BigInteger>, BigInteger>unfold(
                        new Pair<>(BigInteger.ZERO, BigInteger.ONE),
                        s -> Mod005OptionEitherTry.Option.some(
                                new Pair<>(s.first(), new Pair<>(s.second(), s.first().add(s.second())))))
                .take(12)
                .toList();
        var fibsReference = List.of(0, 1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89).stream()
                .map(BigInteger::valueOf).toList();
        boolean fibsOk = fibs.equals(fibsReference);
        System.out.println("  first 12 Fibonacci   = " + fibs + (fibsOk ? " ✓" : " ✗"));

        System.out.println("  all self-checks pass? " + (primesOk && fibsOk));
    }

    public static void main(String[] args) {
        lazyVsStrict();
        streamShape();
        combinators();
        infiniteStreams();
        endToEnd();
        System.out.println("Mod007LazyStreams finished");
    }
}
