package pl.training.functionalfeatures;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/*
A Function that is allowed to fail.

java.util.function has no throwing variants, so when you need one you declare it. The extra type parameter E
carries the checked exception, which keeps the interface honest: a ThrowingFunction<String, Class<?>,
ClassNotFoundException> documents exactly what can go wrong.
*/
@FunctionalInterface
interface ThrowingFunction<T, R, E extends Exception> {

    R apply(T t) throws E;

    /* Adapt to a plain Function by wrapping the checked exception in an unchecked one. */
    static <T, R, E extends Exception> Function<T, R> unchecked(ThrowingFunction<T, R, E> f) {
        return t -> {
            try {
                return f.apply(t);
            } catch (Exception e) {
                throw new IllegalStateException("lambda failed for input: " + t, e);
            }
        };
    }

    /* Adapt to a plain Function that reports failure as an empty Optional instead of throwing. */
    static <T, R, E extends Exception> Function<T, Optional<R>> optional(ThrowingFunction<T, R, E> f) {
        return t -> {
            try {
                return Optional.ofNullable(f.apply(t));
            } catch (Exception e) {
                return Optional.empty();
            }
        };
    }
}

public final class Mod005CheckedExceptionsInLambdas {

    private Mod005CheckedExceptionsInLambdas() {}

    // A sealed result type: success or failure, as a value. Mod015 covers sealed + records properly.
    sealed interface Result<T> permits Ok, Err {}
    record Ok<T>(T value)         implements Result<T> {}
    record Err<T>(String message) implements Result<T> {}

    private static final List<String> CLASS_NAMES =
            List.of("java.lang.String", "java.util.List", "com.example.Missing", "java.time.Instant");

    /*
    Why a lambda cannot throw a checked exception

    Function<T,R>.apply is declared as:

        R apply(T t);          // no throws clause

    A lambda is an implementation of that method, and an implementation may not throw checked exceptions the
    interface does not declare. So this does not compile:

        Function<String, Class<?>> f = name -> Class.forName(name);
        // error: unreported exception ClassNotFoundException; must be caught or declared to be thrown

    The mechanical workaround is a try/catch inside the lambda body. It compiles, but it is exactly the noise
    lambdas were supposed to remove: five lines of ceremony around one line of work, repeated at every call
    site, and it forces a decision (rethrow? return null? skip?) into a place with no context to decide it.

    Note that this is a limitation of the INTERFACE, not of lambdas. Callable<V>.call() declares `throws
    Exception`, so a lambda targeting Callable may throw anything.
    */
    static void whyLambdasCannotThrow() {
        System.out.println("[Section 1] the problem");

        // Does not compile:
        //     Function<String, Class<?>> f = name -> Class.forName(name);

        // The mechanical workaround - correct, but this is 5 lines per call site.
        Function<String, Class<?>> inlineTryCatch = name -> {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                return null;                       // and now every caller must null-check
            }
        };

        var loaded = CLASS_NAMES.stream().map(inlineTryCatch).toList();
        System.out.println("  inline try/catch -> " + loaded);
        System.out.println("  ^ a null in the middle: the failure lost its identity");
    }

    /*
    A throwing functional interface

    Declare the SAM with a `throws E` clause (see ThrowingFunction at the top of this file) and provide static
    adapters that convert it into something java.util.function accepts. The lambda body stays clean; the
    decision about what failure MEANS moves into the adapter, where it is written once.

    - ThrowingFunction.unchecked(f)  - wrap the checked exception in an unchecked one. Use at a boundary where
      failure is genuinely fatal and should abort the pipeline.
    - ThrowingFunction.optional(f)   - turn failure into Optional.empty(). Use when failure means "skip this
      element" and the pipeline should keep going.

    The same recipe applies to the other shapes: ThrowingSupplier, ThrowingConsumer, ThrowingPredicate.
    */
    static void throwingFunctionalInterface() {
        System.out.println("[Section 2] ThrowingFunction + adapters");

        // The lambda body is now just the work: no try, no catch, no null.
        ThrowingFunction<String, Class<?>, ClassNotFoundException> load = Class::forName;

        // Adapter 1 - failure aborts.
        try {
            CLASS_NAMES.stream().map(ThrowingFunction.unchecked(load)).toList();
        } catch (IllegalStateException e) {
            System.out.println("  unchecked() aborted: " + e.getMessage());
        }

        // Adapter 2 - failure skips the element, and the pipeline survives.
        var survivors = CLASS_NAMES.stream()
                .map(ThrowingFunction.optional(load))
                .flatMap(Optional::stream)
                .map(Class::getSimpleName)
                .toList();
        System.out.println("  optional()  kept:    " + survivors);
    }

    /*
    Sneaky throw - and why it is a trap

    Generics erasure lets you throw a checked exception where the compiler believes an unchecked one is thrown:

        @SuppressWarnings("unchecked")
        static <E extends Throwable> RuntimeException sneak(Throwable t) throws E { throw (E) t; }

    The cast is a no-op at runtime, so the original exception propagates with its type and stack trace intact -
    no wrapper, no lost cause. Libraries (Lombok's @SneakyThrows, jOOQ, Jackson) use exactly this.

    The cost: the exception is now invisible to the compiler. A caller cannot `catch (ClassNotFoundException e)`
    around a method that does not declare it - javac rejects that catch clause as unreachable. You have taken a
    documented failure mode out of the type system, and the only way back is a `catch (Exception e)` plus an
    instanceof.

    Verdict: legitimate for infrastructure that must preserve an exception across an interface it does not
    control. In application code, prefer unchecked() (an honest wrapper) or optional()/Result (a value).
    */
    static void sneakyThrow() {
        System.out.println("[Section 3] sneaky throw");

        Function<String, Class<?>> sneaky = name -> {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                throw sneak(e);                    // rethrown unwrapped, but invisible to the compiler
            }
        };

        try {
            sneaky.apply("com.example.Missing");
        } catch (Exception e) {
            // Cannot write `catch (ClassNotFoundException e)` here: the compiler does not believe it
            // can be thrown, and rejects the clause. We are back to runtime type inspection.
            System.out.println("  caught (as Exception): " + e.getClass().getName());
            System.out.println("  cause chain intact:    " + (e.getCause() == null ? "no wrapper added" : "wrapped"));
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException sneak(Throwable t) throws E {
        throw (E) t;
    }

    /*
    Failure as a value

    The functional answer to "this may fail" is not an exception at all - it is a return type that has room for
    the failure. Optional says only "no value"; a two-sided type also says WHY.

    - Optional<T>       - failure carries no information. Fine for lookups.
    - Result<T> / Either<L,R> / Try<T> - failure carries a message, an error code, an exception.

    Once failure is a value, it flows through map/filter/collect like any other value: no control flow, no
    stack unwinding, and a stream can report every failure instead of dying on the first one.

    pl.training.functionalprogramming.Mod005OptionEitherTry builds Option, Either and Try from scratch and
    Mod006ValidationAndApplicative shows how to accumulate several failures at once.
    */
    static void failureAsAValue() {
        System.out.println("[Section 4] failure as a value");

        List<Result<Class<?>>> results = CLASS_NAMES.stream().map(Mod005CheckedExceptionsInLambdas::tryLoad).toList();

        // Both outcomes survive the pipeline and can be reported together.
        var loaded = results.stream().filter(r -> r instanceof Ok).count();
        results.forEach(r -> System.out.println("  " + switch (r) {
            case Ok<Class<?>>(Class<?> c) -> "ok  " + c.getSimpleName();
            case Err<Class<?>>(String m)  -> "err " + m;
        }));
        System.out.println("  " + loaded + "/" + results.size() + " loaded, and nothing was thrown");
    }

    private static Result<Class<?>> tryLoad(String name) {
        try {
            return new Ok<>(Class.forName(name));
        } catch (ClassNotFoundException e) {
            return new Err<>("not on the classpath: " + name);
        }
    }

    /*
    Choosing a strategy

    Put the decision where the context lives, not inside the lambda:

    | Failure means                        | Use                                    |
    |--------------------------------------|----------------------------------------|
    | a bug / unrecoverable configuration  | unchecked() - wrap and let it abort    |
    | this element is not interesting      | optional() + flatMap(Optional::stream) |
    | the caller must be told what failed  | Result / Either                        |
    | crossing an interface you don't own  | sneaky throw, documented               |

    Two rules that survive every strategy:

    1. Never swallow an exception into a null inside a lambda (Section 1). Null is the one option that keeps
       neither the value nor the reason.
    2. Never catch inside the lambda when the same catch would do in the terminal operation. A pipeline that
       throws once at the end is easier to read than one that catches per element.
    */
    static void choosingAStrategy() {
        System.out.println("[Section 5] one pipeline, three policies");

        ThrowingFunction<String, Class<?>, ClassNotFoundException> load = Class::forName;

        System.out.println("  skip failures    = " + CLASS_NAMES.stream()
                .map(ThrowingFunction.optional(load)).flatMap(Optional::stream).count() + " kept");

        var failures = new java.util.ArrayList<String>();
        CLASS_NAMES.stream().map(Mod005CheckedExceptionsInLambdas::tryLoad)
                .forEach(r -> { if (r instanceof Err<Class<?>>(String m)) failures.add(m); });
        System.out.println("  report failures  = " + failures);

        // toList(), not count(): count() on a sized source is allowed to skip map() entirely,
        // so the failure would never happen. Mod006 §4 explains that optimisation.
        System.out.println("  abort on failure = " + attempt(() -> CLASS_NAMES.stream()
                .map(ThrowingFunction.unchecked(load)).toList().size()));
    }

    private static String attempt(java.util.function.Supplier<Object> body) {
        try {
            return "completed with " + body.get();
        } catch (RuntimeException e) {
            return "aborted (" + e.getCause().getClass().getSimpleName() + ")";
        }
    }

    public static void main(String[] args) {
        whyLambdasCannotThrow();
        throwingFunctionalInterface();
        sneakyThrow();
        failureAsAValue();
        choosingAStrategy();
        System.out.println("Mod005CheckedExceptionsInLambdas finished");
    }
}
