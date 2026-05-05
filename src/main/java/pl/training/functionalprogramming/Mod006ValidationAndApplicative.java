package pl.training.functionalprogramming;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class Mod006ValidationAndApplicative {

    private Mod006ValidationAndApplicative() {}

    // =================================================================================================
    // Validation<E, A>
    // =================================================================================================

    public sealed interface Validation<E, A> permits Valid, Invalid {

        static <E, A> Validation<E, A> valid(A value) { return new Valid<>(value); }
        static <E, A> Validation<E, A> invalid(E error) { return new Invalid<>(List.of(error)); }
        static <E, A> Validation<E, A> invalidAll(List<E> errors) { return new Invalid<>(List.copyOf(errors)); }

        default <B> Validation<E, B> map(Function<A, B> f) {
            return switch (this) {
                case Valid<E, A>(A v)            -> valid(f.apply(v));
                case Invalid<E, A>(List<E> es)   -> invalidAll(es);
            };
        }

        // applicative map2: combine two independent validations, accumulating errors
        static <E, A, B, R> Validation<E, R> map2(Validation<E, A> a, Validation<E, B> b,
                                                  BiFunction<A, B, R> f) {
            return switch (a) {
                case Valid<E, A>(A va) -> switch (b) {
                    case Valid<E, B>(B vb)            -> valid(f.apply(va, vb));
                    case Invalid<E, B>(List<E> bes)   -> invalidAll(bes);
                };
                case Invalid<E, A>(List<E> aes) -> switch (b) {
                    case Valid<E, B> __                -> invalidAll(aes);
                    case Invalid<E, B>(List<E> bes)    -> {
                        var combined = new ArrayList<E>(aes); combined.addAll(bes);
                        yield invalidAll(combined);
                    }
                };
            };
        }

        // map3 derived from map2
        static <E, A, B, C, R> Validation<E, R> map3(Validation<E, A> a, Validation<E, B> b,
                                                     Validation<E, C> c,
                                                     TriFn<A, B, C, R> f) {
            return map2(map2(a, b, (x, y) -> (Function<C, R>) cv -> f.apply(x, y, cv)), c,
                    Function::apply);
        }
        @FunctionalInterface interface TriFn<A, B, C, R> { R apply(A a, B b, C c); }
    }
    public record Valid<E, A>(A value)              implements Validation<E, A> {}
    public record Invalid<E, A>(List<E> errors)     implements Validation<E, A> {}

    // =================================================================================================
    // Domain
    // =================================================================================================

    record RegisterUserCommand(String email, String password, int age) {}

    private static final Pattern EMAIL_RE = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    static Validation<String, String> validateEmail(String email) {
        if (email == null || email.isBlank()) return Validation.invalid("email: must not be blank");
        if (!EMAIL_RE.matcher(email).matches()) return Validation.invalid("email: must look like a@b.c");
        return Validation.valid(email);
    }

    static Validation<String, String> validatePassword(String pwd) {
        if (pwd == null || pwd.length() < 8) return Validation.invalid("password: must be ≥ 8 characters");
        return Validation.valid(pwd);
    }

    static Validation<String, Integer> validateAge(int age) {
        if (age < 0 || age > 120) return Validation.invalid("age: must be between 0 and 120");
        return Validation.valid(age);
    }

    static Validation<String, RegisterUserCommand> validate(RegisterUserCommand input) {
        return Validation.map3(
                validateEmail(input.email()),
                validatePassword(input.password()),
                validateAge(input.age()),
                RegisterUserCommand::new);
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    /*
    Why Either is not enough

    Either<E, A>.flatMap chains short-circuit on the first Left:

    parseEmail(input)
        .flatMap(email -> parsePassword(input))      // skipped if email failed
        .flatMap(pw    -> parseAge(input))           // skipped if email or password failed

    For form validation we usually want the opposite behaviour: try every field, collect every error, and only
    report success when all of them passed.

    That's Validation<E, A>: a parallel-style composition that accumulates errors instead of stopping at the first
    one.
    */
    static void whyEitherIsNotEnough() {
        System.out.println("[Section 1] why Either is not enough");
        // Conceptual illustration with Validation built up step-by-step.
        // If we used Either.flatMap to chain three field validations, the first failure
        // would short-circuit and we would never see the others. Demo:
        var firstStep  = validateEmail("not-an-email");        // Invalid("email: ...")
        var secondStep = validatePassword("short");            // Invalid("password: ...")
        // With Either + flatMap, the second never runs. With Validation + map2 we get both:
        System.out.println("  email check     -> " + firstStep);
        System.out.println("  password check  -> " + secondStep);
        System.out.println("  Either flatMap would only show the email error;");
        System.out.println("  Validation map2 will combine BOTH (next sections).");
    }

    /*
    Validation<E, A>

    sealed interface Validation<E, A> permits Valid, Invalid {}
    record Valid<E, A>(A value) implements Validation<E, A> {}
    record Invalid<E, A>(List<E> errors) implements Validation<E, A> {}

    - Invalid carries a list of errors, not a single one.
    - valid(x) and invalid(error) are the constructors.
    - map(f) runs only on Valid; on Invalid it returns the same errors.
    */
    static void validationDemo() {
        System.out.println("[Section 2] Validation");
        System.out.println("  validateEmail(\"\")         = " + validateEmail(""));
        System.out.println("  validateEmail(\"a@b.c\")    = " + validateEmail("a@b.c"));
        System.out.println("  validateAge(-1)            = " + validateAge(-1));
    }

    /*
    The applicative pattern — map2 / map3 / mapN

    The key combinator is map2:

    Validation<E, A> a;
    Validation<E, B> b;
    BiFunction<A, B, R> combine;
    Validation<E, R> result = map2(a, b, combine);

    Cases:
    - both Valid → Valid(combine(a, b)),
    - one Invalid → propagate its errors,
    - both Invalid → concatenate their error lists.

    map3/map4/mapN are built on top of map2. The pattern is called applicative because it lets you apply an
    N-argument function to N independent effectful values.
    */
    static void applicativePattern() {
        System.out.println("[Section 3] applicative — map2 / map3");
        var ok    = Validation.valid("alice@example.com");
        var pwOk  = Validation.valid("p4ssw0rd");
        var ageOk = Validation.valid(30);
        var combined = Validation.map3(ok, pwOk, ageOk, RegisterUserCommand::new);
        System.out.println("  all valid → " + combined);
    }

    /*
    Functor / Applicative / Monad — naming the abstractions

    Three nested abstractions defined by the operations they support:

    - Functor: map(F<A>, A->B) → F<B>. "I can transform the inside." Examples: Option, Either, Try, Validation,
      List, Stream.
    - Applicative: Functor + pure(A) → F<A> + map2(F<A>, F<B>, ...). "I can combine independent values." All
      examples above are applicatives.
    - Monad: Applicative + flatMap(F<A>, A->F<B>) → F<B>. "I can chain dependent computations."

    Validation is an applicative but not a monad: a lawful flatMap would have to short-circuit on Invalid (because
    the next step depends on the previous value), losing the accumulation property. That's why applicative is the
    right level of abstraction for form validation.
    */
    static void abstractionsHierarchy() {
        System.out.println("[Section 4] Functor / Applicative / Monad");
        System.out.println("  Validation is an Applicative but NOT a Monad");
        System.out.println("  (a monadic flatMap would short-circuit and lose accumulation)");
    }

    /*
    End-to-end form validation with assertions

    Validate a RegisterUserCommand(email, password, age):

    - email: non-blank + matches a tiny regex.
    - password: at least 8 characters.
    - age: between 0 and 120.

    Three probes:
    1. all three fields valid → Valid, 0 errors.
    2. one bad field (email) → Invalid with 1 error.
    3. two bad fields (email + age) → Invalid with 2 errors.

    The main checks the error count against the expected.
    */
    static void endToEnd() {
        System.out.println("[Section 5] form validation — assertions");

        record Probe(String label, RegisterUserCommand input, int expectedErrors) {}
        var probes = List.of(
                new Probe("all valid",                 new RegisterUserCommand("alice@example.com", "p4ssw0rd", 30), 0),
                new Probe("invalid email",             new RegisterUserCommand("not-an-email",       "p4ssw0rd", 30), 1),
                new Probe("invalid email + age",       new RegisterUserCommand("not-an-email",       "p4ssw0rd", -1), 2),
                new Probe("all three invalid",         new RegisterUserCommand("",                  "short",   200), 3));

        boolean allOk = true;
        for (var p : probes) {
            var result = validate(p.input);
            int errors = result instanceof Invalid<String, RegisterUserCommand>(List<String> es) ? es.size() : 0;
            boolean ok = errors == p.expectedErrors;
            if (!ok) allOk = false;
            System.out.printf("  [%-22s] %d error(s) (expected %d) %s%n",
                    p.label, errors, p.expectedErrors, ok ? "✓" : "✗");
            if (result instanceof Invalid<String, RegisterUserCommand>(List<String> es)) {
                for (String e : es) System.out.println("      " + e);
            }
        }
        System.out.println("  all probes match expected counts? " + allOk);
    }

    public static void main(String[] args) {
        whyEitherIsNotEnough();
        validationDemo();
        applicativePattern();
        abstractionsHierarchy();
        endToEnd();
        System.out.println("Mod006ValidationAndApplicative finished");
    }
}
