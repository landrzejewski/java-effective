package pl.training.functionalprogramming;

import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

// =================================================================================================
// Section 1: Option<A>
// =================================================================================================

/*
## Option<A>

The "value or nothing" type:

```
sealed interface Option<A> permits Some, None {}
record Some<A>(A value) implements Option<A> {}
record None<A>()        implements Option<A> {}
```

- Replaces nullable returns when "absent" is a *normal* outcome.
- Combinators: `map`, `flatMap`, `getOrElse`, `orElse`, `filter`.
- Java's standard `Optional<A>` is the same idea; this module rolls its
own to make the sealed-type pattern explicit and to compose with the
custom `Either` and `Try` types below.
*/

// =================================================================================================
// Section 2: Either<E, A>
// =================================================================================================

/*
## Either<E, A>

The "value or rich error" type:

```
sealed interface Either<E, A> permits Left, Right {}
record Left<E, A>(E error) implements Either<E, A> {}
record Right<E, A>(A value)  implements Either<E, A> {}
```

By convention `Right` is the success side; combinators (`map`, `flatMap`)
operate on the right.

Use it when "error" carries information you want the caller to react to —
not just "absent" but a typed error value (`InvalidEmail`,
`NotFound("user u-1")`, …).

`flatMap` short-circuits on `Left`: the first failure in a chain is the
final result. (Need accumulation? See Mod006.)
*/

// =================================================================================================
// Section 3: Try<A>
// =================================================================================================

/*
## Try<A>

The "value or exception" type:

```
sealed interface Try<A> permits Success, Failure {}
record Success<A>(A value)      implements Try<A> {}
record Failure<A>(Throwable t)  implements Try<A> {}
```

`Try.of(supplier)` runs a throwing call and wraps the outcome.

Use it at the boundary with Java APIs that throw (`Integer.parseInt`,
file IO, `Class.forName`). Once the exception is in the `Try`, you can
keep composing without writing `try`/`catch` everywhere.

Beware: `Try` catches `Exception`, not `Error` — `OutOfMemoryError` and
similar should still crash the program.
*/

// =================================================================================================
// Section 4: When to pick which
// =================================================================================================

/*
## When to pick which

| Situation                              | Choose      |
|----------------------------------------|-------------|
| Key may be missing from a map          | Option      |
| Validation produces a typed error      | Either      |
| Parsing user input that may throw      | Try         |
| Multiple errors must be aggregated     | Validation (Mod006) |

For "an exception in disguise", prefer `Either` over `Try`: the error
type is named and the caller can pattern-match on it. `Try` is the
glue between the throwing world and the value world.
*/

// =================================================================================================
// Section 5: Conversion bridges
// =================================================================================================

/*
## Conversion bridges

The three types are interchangeable when you have the data each one
needs:

- `Option.toEither(elseError)` — `None → Left(elseError)`,
                                 `Some(x) → Right(x)`.
- `Try.toEither(mapException)` — `Failure(t) → Left(mapException(t))`,
                                 `Success(x) → Right(x)`.
- `Either.toOption()`          — drops the error payload.

These bridges let you choose the *most expressive* type per layer and
adapt at the boundaries.
*/

// =================================================================================================
// Section 6: End-to-end CRM lookup
// =================================================================================================

/*
## End-to-end CRM lookup

Three layered operations:

- `findUserId(email)` — `Option<UserId>`.
- `parseAge(rawString)` — `Try<Integer>`.
- `loadProfile(userId)` — `Either<String, Profile>` (typed error).

Compose them with `flatMap` into a single `Either<String, Profile>` that
is `Right` only when all three steps succeeded; otherwise the first
failure in the chain wins.
*/

public final class Mod005OptionEitherTry {

    private Mod005OptionEitherTry() {}

    // =================================================================================================
    // Option, Either, Try — sealed implementations
    // =================================================================================================

    public sealed interface Option<A> permits Some, None {

        @SuppressWarnings("unchecked")
        static <A> Option<A> none() { return (Option<A>) None.INSTANCE; }
        static <A> Option<A> some(A value) { return new Some<>(value); }
        static <A> Option<A> ofNullable(A v) { return v == null ? none() : some(v); }

        default <B> Option<B> map(Function<A, B> f) {
            return switch (this) {
                case Some<A>(A v) -> some(f.apply(v));
                case None<A> __   -> none();
            };
        }
        default <B> Option<B> flatMap(Function<A, Option<B>> f) {
            return switch (this) {
                case Some<A>(A v) -> f.apply(v);
                case None<A> __   -> none();
            };
        }
        default A getOrElse(A fallback) {
            return this instanceof Some<A>(A v) ? v : fallback;
        }
        default <E> Either<E, A> toEither(Supplier<E> elseError) {
            return switch (this) {
                case Some<A>(A v) -> Either.right(v);
                case None<A> __   -> Either.left(elseError.get());
            };
        }
    }
    public record Some<A>(A value) implements Option<A> {}
    public record None<A>()        implements Option<A> {
        static final None<?> INSTANCE = new None<>();
    }

    public sealed interface Either<E, A> permits Left, Right {

        static <E, A> Either<E, A> left(E error) { return new Left<>(error); }
        static <E, A> Either<E, A> right(A value) { return new Right<>(value); }

        default <B> Either<E, B> map(Function<A, B> f) {
            return switch (this) {
                case Right<E, A>(A v) -> right(f.apply(v));
                case Left<E, A>(E e)  -> left(e);
            };
        }
        default <B> Either<E, B> flatMap(Function<A, Either<E, B>> f) {
            return switch (this) {
                case Right<E, A>(A v) -> f.apply(v);
                case Left<E, A>(E e)  -> left(e);
            };
        }
        default Option<A> toOption() {
            return switch (this) {
                case Right<E, A>(A v) -> Option.some(v);
                case Left<E, A> __    -> Option.none();
            };
        }
    }
    public record Left<E, A>(E error)  implements Either<E, A> {}
    public record Right<E, A>(A value) implements Either<E, A> {}

    public sealed interface Try<A> permits Success, Failure {

        @FunctionalInterface interface ThrowingSupplier<A> { A get() throws Exception; }

        static <A> Try<A> of(ThrowingSupplier<A> supplier) {
            try { return new Success<>(supplier.get()); }
            catch (Exception e) { return new Failure<>(e); }
        }

        default <B> Try<B> map(Function<A, B> f) {
            return switch (this) {
                case Success<A>(A v)      -> new Success<>(f.apply(v));
                case Failure<A>(Throwable t) -> new Failure<>(t);
            };
        }
        default <B> Try<B> flatMap(Function<A, Try<B>> f) {
            return switch (this) {
                case Success<A>(A v)      -> f.apply(v);
                case Failure<A>(Throwable t) -> new Failure<>(t);
            };
        }
        default <E> Either<E, A> toEither(Function<Throwable, E> mapEx) {
            return switch (this) {
                case Success<A>(A v)      -> Either.right(v);
                case Failure<A>(Throwable t) -> Either.left(mapEx.apply(t));
            };
        }
    }
    public record Success<A>(A value)         implements Try<A> {}
    public record Failure<A>(Throwable error) implements Try<A> {}

    // =================================================================================================
    // CRM domain
    // =================================================================================================

    record Profile(String userId, String name, int age) {}

    private static final Map<String, String> EMAIL_TO_ID = Map.of(
            "alice@example.com", "u-1",
            "bob@example.com",   "u-2");
    private static final Map<String, Profile> PROFILES = Map.of(
            "u-1", new Profile("u-1", "Alice", 30),
            "u-2", new Profile("u-2", "Bob",   28));

    static Option<String> findUserId(String email) {
        return Option.ofNullable(EMAIL_TO_ID.get(email));
    }
    static Try<Integer> parseAge(String s) {
        return Try.of(() -> Integer.parseInt(s));
    }
    static Either<String, Profile> loadProfile(String userId) {
        return PROFILES.containsKey(userId)
                ? Either.right(PROFILES.get(userId))
                : Either.left("no profile for " + userId);
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    static void optionSection() {
        System.out.println("[Section 1] Option");
        Option<String> s = Option.some("hi");
        Option<String> n = Option.none();
        System.out.println("  some.map(toUpper).getOrElse('') = " + s.map(String::toUpperCase).getOrElse(""));
        System.out.println("  none.map(toUpper).getOrElse('?') = " + n.map(String::toUpperCase).getOrElse("?"));
    }

    static void eitherSection() {
        System.out.println("[Section 2] Either");
        Either<String, Integer> r = Either.right(10);
        Either<String, Integer> l = Either.left("boom");
        System.out.println("  right.map(*2)     = " + r.map(x -> x * 2));
        System.out.println("  left.map(*2)      = " + l.map(x -> x * 2));
        System.out.println("  right.flatMap(>>0)= " + r.flatMap(x -> x > 0 ? Either.right(x) : Either.left("neg")));
    }

    static void trySection() {
        System.out.println("[Section 3] Try");
        Try<Integer> ok  = Try.of(() -> Integer.parseInt("42"));
        Try<Integer> bad = Try.of(() -> Integer.parseInt("zonk"));
        System.out.println("  ok  = " + ok);
        System.out.println("  bad = " + bad.getClass().getSimpleName() + " (NumberFormatException)");
    }

    static void decisionMatrix() {
        System.out.println("[Section 4] decision matrix");
        System.out.println("  map lookup     -> Option");
        System.out.println("  validation     -> Either");
        System.out.println("  throwing parse -> Try");
        System.out.println("  multiple errs  -> Validation (Mod006)");
    }

    static void conversionBridges() {
        System.out.println("[Section 5] conversion bridges");
        var idOption = findUserId("alice@example.com");
        var idEither = idOption.toEither(() -> "no id");
        System.out.println("  Option → Either: " + idEither);

        var parsedTry = parseAge("zonk");
        Either<String, Integer> ageEither = parsedTry.toEither(t -> "parse error: " + t.getClass().getSimpleName());
        System.out.println("  Try    → Either: " + ageEither);
    }

    static void endToEnd() {
        System.out.println("[Section 6] end-to-end CRM lookup");

        record Probe(String email, String rawAge, boolean expectedRight) {}
        var probes = java.util.List.of(
                new Probe("alice@example.com", "30",   true),
                new Probe("ghost@example.com", "30",   false),
                new Probe("bob@example.com",   "zonk", false));

        boolean allOk = true;
        for (var p : probes) {
            Either<String, Profile> result = findUserId(p.email)
                    .toEither(() -> "no user for email " + p.email)
                    .flatMap(id -> parseAge(p.rawAge)
                            .toEither(t -> "bad age: " + t.getClass().getSimpleName())
                            .flatMap(__ -> loadProfile(id)));

            boolean isRight = result instanceof Right;
            boolean ok = isRight == p.expectedRight;
            if (!ok) allOk = false;
            System.out.printf("  %-18s + %-5s -> %-65s %s%n",
                    p.email, p.rawAge, result, ok ? "✓" : "✗");
        }
        System.out.println("  all probes match expected? " + allOk);
    }

    public static void main(String[] args) {
        optionSection();
        eitherSection();
        trySection();
        decisionMatrix();
        conversionBridges();
        endToEnd();
        System.out.println("Mod005OptionEitherTry finished");
    }
}
