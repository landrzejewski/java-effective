package pl.training.functionalprogramming;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/*
Putting failure into the return type

Java has two built-in ways for a method to say "that did not work": return null, or throw. Both hide the
outcome from the type system, and both are the source of the two most common runtime failures in the
language.

- null says nothing about *why*, and nothing in the signature warns you that it can happen. The
  compiler will happily let you dereference it. This is the "billion dollar mistake" its own inventor
  apologised for.
- An exception does carry a reason, but it is a second, invisible return channel. A method declared
  `Profile load(String id)` can also, unannounced, unwind the stack instead. Every caller has to know
  this from documentation rather than from the type, and checked exceptions - Java's attempt to fix
  exactly this - do not survive contact with lambdas and streams.

The alternative in this module is boring and effective: make failure an ordinary value, so that the
return type describes every possible outcome. Three types, differing only in how much they say about the
failure:

    Option<A>       "an A, or nothing"                  - absence is normal and needs no explanation
    Either<E, A>    "an A, or an E explaining why not"  - failure carries typed information
    Try<A>          "an A, or the exception that was thrown" - the bridge from throwing Java code

All three are *sealed* interfaces with exactly two implementations, which is what makes them work: the
compiler knows there are only two cases, so a switch over them is checked for exhaustiveness. You cannot
forget the failure branch, because the code will not compile.

And all three carry the same two combinators, map and flatMap, which is what makes them composable
rather than merely descriptive. Java's own Optional is the same design; we rebuild it here so the
sealed-type mechanics are visible and so the three types can convert into each other.
*/

public final class Mod005OptionEitherTry {

    private Mod005OptionEitherTry() {}

    // =================================================================================================
    // Option, Either, Try - sealed implementations
    // =================================================================================================

    /*
    Option<A> - "a value, or nothing", with no explanation of why.

    Read the combinators as a pair:
      map(f)      f returns a plain B.        Use when the next step cannot itself fail.
      flatMap(f)  f returns an Option<B>.     Use when the next step can also come back empty.

    Using map where you needed flatMap gives you Option<Option<B>>, which is the standard signal that
    you picked the wrong one. This distinction is identical to Optional.map vs Optional.flatMap and to
    Stream.map vs Stream.flatMap - once you have it for one type, you have it for all of them.
    */
    public sealed interface Option<A> permits Some, None {

        @SuppressWarnings("unchecked")
        static <A> Option<A> none() { return (Option<A>) None.INSTANCE; }
        static <A> Option<A> some(A value) { return new Some<>(value); }
        /** The boundary function: turn a legacy nullable value into an Option, once, at the edge. */
        static <A> Option<A> ofNullable(A v) { return v == null ? none() : some(v); }

        default <B> Option<B> map(Function<A, B> f) {
            return switch (this) {
                case Some<A>(A v) -> some(f.apply(v));
                case None<A> _    -> none();          // nothing to apply f to; f is never called
            };
        }
        default <B> Option<B> flatMap(Function<A, Option<B>> f) {
            return switch (this) {
                case Some<A>(A v) -> f.apply(v);      // f already returns an Option, so no wrapping
                case None<A> _    -> none();
            };
        }
        default Option<A> filter(Predicate<A> p) {
            return switch (this) {
                case Some<A>(A v) -> p.test(v) ? this : none();   // a Some can become a None
                case None<A> _    -> this;
            };
        }
        /** Note the Supplier: the fallback is only built if it is actually needed. */
        default Option<A> orElse(Supplier<Option<A>> alternative) {
            return this instanceof Some<A> ? this : alternative.get();
        }
        default A getOrElse(A fallback) {
            return this instanceof Some<A>(A v) ? v : fallback;
        }
        /** Upgrade to Either by supplying the explanation that Option never had. */
        default <E> Either<E, A> toEither(Supplier<E> elseError) {
            return switch (this) {
                case Some<A>(A v) -> Either.right(v);
                case None<A> _    -> Either.left(elseError.get());
            };
        }
    }
    public record Some<A>(A value) implements Option<A> {}
    public record None<A>()        implements Option<A> {
        static final None<?> INSTANCE = new None<>();
    }

    /*
    Either<E, A> - "a value, or a typed reason it is missing".

    The convention, universal across languages: Right is success, Left is failure. (The mnemonic is that
    "right" also means "correct".) Every combinator therefore works on the Right side and passes a Left
    through untouched - the type is said to be "right-biased".

    That pass-through behaviour is what gives you short-circuiting: in a chain of flatMaps, the first
    Left becomes the result and no later step runs. This is the same control flow as a sequence of early
    returns, or as a try block with several throwing calls - except it is ordinary data flow, visible in
    the type, with no hidden jump.
    */
    public sealed interface Either<E, A> permits Left, Right {

        static <E, A> Either<E, A> left(E error) { return new Left<>(error); }
        static <E, A> Either<E, A> right(A value) { return new Right<>(value); }

        default <B> Either<E, B> map(Function<A, B> f) {
            return switch (this) {
                case Right<E, A>(A v) -> right(f.apply(v));
                case Left<E, A>(E e)  -> left(e);     // rebuilt only to change the phantom A type
            };
        }
        default <B> Either<E, B> flatMap(Function<A, Either<E, B>> f) {
            return switch (this) {
                case Right<E, A>(A v) -> f.apply(v);
                case Left<E, A>(E e)  -> left(e);     // short circuit: f is never called
            };
        }
        /** Recover: turn a failure back into a success by supplying a value for the error. */
        default A getOrElse(Function<E, A> recover) {
            return switch (this) {
                case Right<E, A>(A v) -> v;
                case Left<E, A>(E e)  -> recover.apply(e);
            };
        }
        /** Downgrade to Option, discarding the reason. Cheap to do, impossible to undo. */
        default Option<A> toOption() {
            return switch (this) {
                case Right<E, A>(A v) -> Option.some(v);
                case Left<E, A> _     -> Option.none();
            };
        }
    }
    public record Left<E, A>(E error)  implements Either<E, A> {}
    public record Right<E, A>(A value) implements Either<E, A> {}

    /*
    Try<A> - "a value, or the exception that was thrown while producing it".

    This is the adapter between the throwing world and the value world. Try.of takes a lambda that is
    allowed to throw (note ThrowingSupplier - the standard Supplier is not allowed to, which is exactly
    why checked exceptions and lambdas fit together so badly), runs it, and captures whichever happened.

    After that one call the exception is a value like any other, so it can be stored, returned, mapped
    over, put in a list, or matched on - none of which you can do with an in-flight throw.

    One deliberate limitation: Try.of catches Exception, not Throwable. Errors - OutOfMemoryError,
    StackOverflowError, LinkageError - signal that the JVM itself is in trouble, and swallowing them into
    a data type would turn a fatal condition into a silently ignored one. They are allowed to propagate.
    */
    public sealed interface Try<A> permits Success, Failure {

        @FunctionalInterface interface ThrowingSupplier<A> { A get() throws Exception; }

        static <A> Try<A> of(ThrowingSupplier<A> supplier) {
            try { return new Success<>(supplier.get()); }
            catch (Exception e) { return new Failure<>(e); }   // Error is deliberately not caught
        }

        default <B> Try<B> map(Function<A, B> f) {
            return switch (this) {
                case Success<A>(A v)         -> new Success<>(f.apply(v));
                case Failure<A>(Throwable t) -> new Failure<>(t);
            };
        }
        default <B> Try<B> flatMap(Function<A, Try<B>> f) {
            return switch (this) {
                case Success<A>(A v)         -> f.apply(v);
                case Failure<A>(Throwable t) -> new Failure<>(t);
            };
        }
        /** Convert the raw exception into a domain error you actually want callers to see. */
        default <E> Either<E, A> toEither(Function<Throwable, E> mapEx) {
            return switch (this) {
                case Success<A>(A v)         -> Either.right(v);
                case Failure<A>(Throwable t) -> Either.left(mapEx.apply(t));
            };
        }
    }
    public record Success<A>(A value)         implements Try<A> {}
    public record Failure<A>(Throwable error) implements Try<A> {}

    // =================================================================================================
    // A tiny CRM domain - one operation per type, so they can be composed in Section 6
    // =================================================================================================

    record Profile(String userId, String name, int age) {}

    private static final Map<String, String> EMAIL_TO_ID = Map.of(
            "alice@example.com", "u-1",
            "bob@example.com",   "u-2");
    private static final Map<String, Profile> PROFILES = Map.of(
            "u-1", new Profile("u-1", "Alice", 30),
            "u-2", new Profile("u-2", "Bob",   28));

    /** Map lookup: "not found" is normal and needs no explanation -> Option. */
    static Option<String> findUserId(String email) {
        return Option.ofNullable(EMAIL_TO_ID.get(email));
    }
    /** Wraps a method that throws -> Try. */
    static Try<Integer> parseAge(String s) {
        return Try.of(() -> Integer.parseInt(s));
    }
    /** Failure carries a message the caller may want to show or log -> Either. */
    static Either<String, Profile> loadProfile(String userId) {
        return PROFILES.containsKey(userId)
                ? Either.right(PROFILES.get(userId))
                : Either.left("no profile for " + userId);
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    /*
    Section 1 - Option<A>

        sealed interface Option<A> permits Some, None {}
        record Some<A>(A value) implements Option<A> {}
        record None<A>()        implements Option<A> {}

    Use it when "absent" is an ordinary, expected outcome that needs no justification: a key that is not
    in a map, a filter that matched nothing, an optional configuration setting.

    Combinators implemented above: map, flatMap, filter, orElse, getOrElse, toEither.

    The behaviour to notice in the demo: calling .map on a None does not throw and does not run the
    function - it just returns None. That is the whole trick. Where null forces every caller to write an
    if, Option pushes the check inside the combinator, so the absent case is handled once, correctly, by
    the type rather than repeatedly by hand.

    Java's own java.util.Optional is this type with a different name. Two practical differences: Optional
    was designed for return values only (do not use it for fields or parameters), and it is not sealed,
    so you cannot switch over it exhaustively.
    */
    static void optionSection() {
        System.out.println("[Section 1] Option");
        Option<String> s = Option.some("hi");
        Option<String> n = Option.none();

        System.out.println("  some.map(toUpper).getOrElse(\"\")  = " + s.map(String::toUpperCase).getOrElse(""));
        System.out.println("  none.map(toUpper).getOrElse(\"?\") = " + n.map(String::toUpperCase).getOrElse("?")
                + "   <- toUpperCase was never called");
        System.out.println("  some.filter(length > 5)           = " + s.filter(x -> x.length() > 5)
                + "   <- a Some can become a None");
        System.out.println("  none.orElse(() -> some(\"fallback\")) = " + n.orElse(() -> Option.some("fallback")));
    }

    /*
    Section 2 - Either<E, A>

        sealed interface Either<E, A> permits Left, Right {}
        record Left<E, A>(E error)  implements Either<E, A> {}
        record Right<E, A>(A value) implements Either<E, A> {}

    Use it when the caller needs to know *why*, and to react differently to different reasons. E does not
    have to be a String - the interesting version is a sealed hierarchy of error cases (NotFound,
    Forbidden, RateLimited), which lets the caller switch over the failures exhaustively, just like the
    successes.

    The demo shows the two behaviours that matter:

    - map and flatMap only touch the Right side. Applied to a Left, they hand the same Left straight
      back and never call the function.
    - Consequently, in a flatMap chain the first Left wins and everything after it is skipped. That is
      short-circuiting, and it is the correct default for a sequence of dependent steps: if you could not
      load the user, there is no point trying to load their orders.

    When you specifically do *not* want to stop at the first failure - validating a form, where you want
    every bad field reported at once - Either is the wrong tool, and Mod006 explains why that is a
    fundamental property rather than a missing method.
    */
    static void eitherSection() {
        System.out.println("[Section 2] Either");
        Either<String, Integer> r = Either.right(10);
        Either<String, Integer> l = Either.left("boom");

        System.out.println("  right.map(*2)  = " + r.map(x -> x * 2));
        System.out.println("  left.map(*2)   = " + l.map(x -> x * 2) + "   <- the function never ran");

        // A chain of dependent steps. The first Left ends it.
        var chain = r.flatMap(x -> x > 0 ? Either.right(x) : Either.left("not positive"))
                     .flatMap(x -> x < 5 ? Either.right(x) : Either.left("too big"))
                     .flatMap(x -> Either.right(x * 100));      // never reached
        System.out.println("  chained flatMaps, second one fails = " + chain);
        System.out.println("  recovered with getOrElse           = " + chain.getOrElse(e -> -1));
    }

    /*
    Section 3 - Try<A>

        sealed interface Try<A> permits Success, Failure {}
        record Success<A>(A value)       implements Try<A> {}
        record Failure<A>(Throwable t)   implements Try<A> {}

    Try.of(supplier) runs code that may throw and captures whichever outcome occurred. Use it at the
    boundary with Java APIs that signal failure by throwing: Integer.parseInt, file and network IO,
    reflection, JDBC.

    The value is that after that single call, you stop writing try/catch. A Failure flows through map and
    flatMap untouched, exactly as a Left does, so a five-step parse-and-transform pipeline needs one
    Try.of at the start rather than five nested try blocks.

    In application code, prefer converting to Either quickly (see Section 5). A Throwable is a bad error
    type: it carries a stack trace nobody wants to show a user, its class hierarchy is not yours to
    control, and matching on exception types is far clumsier than matching on your own sealed errors.
    Try is the adapter at the boundary, not the type you should be passing around inside your domain.
    */
    static void trySection() {
        System.out.println("[Section 3] Try");
        Try<Integer> ok  = Try.of(() -> Integer.parseInt("42"));
        Try<Integer> bad = Try.of(() -> Integer.parseInt("zonk"));

        System.out.println("  Try.of(parseInt(\"42\"))   = " + ok);
        System.out.println("  Try.of(parseInt(\"zonk\")) = " + bad.getClass().getSimpleName()
                + " holding a " + ((Failure<Integer>) bad).error().getClass().getSimpleName());
        // The failure flows through the rest of the pipeline without a single try/catch.
        System.out.println("  bad.map(*2).map(+1)      = " + bad.map(x -> x * 2).map(x -> x + 1));
    }

    /*
    Section 4 - which one to reach for

      Situation                                     Choose
      --------------------------------------------- ------------------------
      A key may be missing from a map                Option
      A filter may match nothing                     Option
      Failure has a reason the caller must act on    Either
      Several distinct failure modes to distinguish  Either, with a sealed error type
      Calling an API that throws                     Try, converted to Either immediately
      Every error must be collected, not just the
      first one                                      Validation (Mod006)

    The rule of thumb: pick the *least* informative type that still tells the caller what it needs. An
    Option where an Either was warranted throws away the diagnosis; an Either where an Option would do
    forces every caller to invent and then ignore an error value.

    The one genuinely common mistake is leaving Try in your domain types. Convert at the boundary.
    */
    static void decisionMatrix() {
        System.out.println("[Section 4] decision matrix");
        System.out.println("  map lookup / absent is normal -> Option");
        System.out.println("  caller must know why          -> Either");
        System.out.println("  the API throws                -> Try, then convert to Either");
        System.out.println("  collect every error           -> Validation (Mod006)");
    }

    /*
    Section 5 - conversion bridges

    The three types hold different amounts of information, so converting between them means either
    supplying what is missing or discarding what is not needed:

    - Option.toEither(supplier) - None has no reason attached, so you must provide one.
    - Try.toEither(mapper)      - a Throwable is a poor domain error, so you map it to your own type.
    - Either.toOption()         - drops the reason. Lossy, and one-way.

    This is what lets each layer use the type that suits it and adapt at the seams: a repository returns
    Option because "not found" is unremarkable, the service turns that into an Either with a domain
    error, and the HTTP layer turns the Left into a status code.

    Section 6 uses exactly these bridges to make three functions with three different return types
    compose into one chain.
    */
    static void conversionBridges() {
        System.out.println("[Section 5] conversion bridges");

        var idOption = findUserId("alice@example.com");
        var idEither = idOption.toEither(() -> "no id");        // supply the missing reason
        System.out.println("  Option -> Either : " + idEither);

        var parsedTry = parseAge("zonk");
        Either<String, Integer> ageEither =
                parsedTry.toEither(t -> "parse error: " + t.getClass().getSimpleName());
        System.out.println("  Try    -> Either : " + ageEither);

        System.out.println("  Either -> Option : " + ageEither.toOption() + "   <- reason discarded");
    }

    /*
    Section 6 - end-to-end: three types, three layers, one chain

    Three operations, each returning the type that fits it:

        findUserId(email)   -> Option<String>            absence is normal
        parseAge(raw)       -> Try<Integer>              the underlying call throws
        loadProfile(userId) -> Either<String, Profile>   failure has a reason

    They cannot be chained directly - the types do not line up. So each is bridged to Either first, and
    then flatMap does the rest. The result is a single Either<String, Profile> that is Right only if all
    three steps succeeded, and otherwise holds the message from whichever step failed first.

    Two things to notice in the code below:

    - The nesting exists because each step needs a value from an earlier one (loadProfile needs the id
      that findUserId produced). This is precisely what flatMap is for and precisely why Java's lack of
      do-notation makes it look busier than it is - the same problem appears again in Mod008.
    - `flatMap(_ -> loadProfile(id))` deliberately ignores the parsed age. That is not a bug and not an
      oversight: parseAge is in the chain purely as a validation gate. Its *success or failure* matters,
      its value does not. The unnamed variable `_` says so explicitly, which is exactly why Java added
      it - a named-but-unused parameter would look like a mistake.

    The probes check that each of the three outcomes (all fine, unknown email, unparseable age) produces
    the expected Right or Left.
    */
    static void endToEnd() {
        System.out.println("[Section 6] end-to-end CRM lookup");

        record Probe(String email, String rawAge, boolean expectedRight) {}
        var probes = List.of(
                new Probe("alice@example.com", "30",   true),
                new Probe("ghost@example.com", "30",   false),   // fails at step 1
                new Probe("bob@example.com",   "zonk", false));  // fails at step 2

        boolean allOk = true;
        for (var p : probes) {
            Either<String, Profile> result = findUserId(p.email)
                    .toEither(() -> "no user for email " + p.email)
                    .flatMap(id -> parseAge(p.rawAge)
                            .toEither(t -> "bad age: " + t.getClass().getSimpleName())
                            .flatMap(_ -> loadProfile(id)));   // age validated, value intentionally unused

            boolean isRight = result instanceof Right;
            boolean ok = isRight == p.expectedRight;
            if (!ok) allOk = false;
            System.out.printf(Locale.ROOT, "  %-18s + %-5s -> %-65s %s%n",
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
