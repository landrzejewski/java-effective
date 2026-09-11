package pl.training.functionalprogramming;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;

/*
Collecting every error instead of stopping at the first

Mod005 ended with Either, whose flatMap short-circuits: the first Left becomes the result and nothing
after it runs. For a sequence of dependent steps that is exactly right. For validating a form it is
exactly wrong - a user who submitted four bad fields deserves four messages, not one, followed by three
more round trips to discover the rest.

This module builds Validation<E, A>, which looks almost identical to Either but combines differently:
run every check, and if more than one fails, concatenate their errors.

The reason this needs a whole module rather than a helper method is that the difference is structural,
not cosmetic. flatMap *cannot* accumulate - not "does not", cannot - and understanding why leads
directly to the three abstractions that organise most of functional programming:

    Functor       has map                    "I can transform the value inside"
    Applicative   has map + pure + ap        "I can combine several independent values"
    Monad         has map + pure + flatMap   "I can chain steps where each depends on the last"

Each row can do strictly more than the one above it. Validation deliberately stops at the middle row:
it is an Applicative and not a Monad, and that limitation is what buys the error accumulation. This is
the clearest example in the package of a weaker abstraction being the more useful one.
*/

public final class Mod006ValidationAndApplicative {

    private Mod006ValidationAndApplicative() {}

    // =================================================================================================
    // Validation<E, A>
    //
    // Same two shapes as Either, with one change that drives everything: Invalid holds a LIST of errors
    // rather than a single one, so two failures have somewhere to go when they meet.
    // =================================================================================================

    public sealed interface Validation<E, A> permits Valid, Invalid {

        static <E, A> Validation<E, A> valid(A value) { return new Valid<>(value); }
        static <E, A> Validation<E, A> invalid(E error) { return new Invalid<>(List.of(error)); }
        static <E, A> Validation<E, A> invalidAll(List<E> errors) { return new Invalid<>(List.copyOf(errors)); }

        /** `pure` is the Applicative name for "lift a plain value into the type with no effect".
         *  For Validation that is exactly `valid`; the alias exists so the vocabulary in Section 4
         *  (map + pure + ap) maps onto real methods you can point at. Optional.of and Stream.of are
         *  the same operation for their types. */
        static <E, A> Validation<E, A> pure(A value) { return valid(value); }

        /** map is the Functor operation: change the value inside, leave the shape alone.
         *  On Invalid the function is not called and the errors pass through unchanged. */
        default <B> Validation<E, B> map(Function<A, B> f) {
            return switch (this) {
                case Valid<E, A>(A v)          -> valid(f.apply(v));
                case Invalid<E, A>(List<E> es) -> invalidAll(es);
            };
        }

        /*
        map2 - the operation that makes this type worth having.

        It takes two INDEPENDENT validations and a function to combine their values. Independent is the
        key word: neither input needs the other's result, so both can be evaluated, so both can fail, so
        both sets of errors can be kept. That is impossible for a chain of dependent steps, where the
        second step literally cannot run without the first one's value.

        The four cases, written out explicitly below:

            Valid   + Valid    -> Valid(f(a, b))
            Valid   + Invalid  -> Invalid(b's errors)
            Invalid + Valid    -> Invalid(a's errors)
            Invalid + Invalid  -> Invalid(a's errors ++ b's errors)     <- the accumulating case

        Only the last line differs from what Either would do. Everything else in this module is built on
        this one method.
        */
        static <E, A, B, R> Validation<E, R> map2(Validation<E, A> a, Validation<E, B> b,
                                                  BiFunction<A, B, R> f) {
            return switch (a) {
                case Valid<E, A>(A va) -> switch (b) {
                    case Valid<E, B>(B vb)          -> valid(f.apply(va, vb));
                    case Invalid<E, B>(List<E> bes) -> invalidAll(bes);
                };
                case Invalid<E, A>(List<E> aes) -> switch (b) {
                    case Valid<E, B> _              -> invalidAll(aes);
                    case Invalid<E, B>(List<E> bes) -> {
                        // Both failed. This is the line that distinguishes Validation from Either.
                        var combined = new ArrayList<E>(aes);
                        combined.addAll(bes);
                        yield invalidAll(combined);
                    }
                };
            };
        }

        /*
        ap ("apply") - the standard Applicative operation, and the bridge from map2 to any arity.

        Its signature looks strange the first time:

            ap(Validation<E, Function<A, R>> vf, Validation<E, A> va) -> Validation<E, R>

        Read it as: the FUNCTION is itself inside a Validation, and so is the argument. ap applies one to
        the other while combining the two error lists.

        Why would a function be inside a Validation? Because of partial application (Mod002 Section 4).
        If you take a two-argument function and supply only the first argument, you get back a function
        waiting for the second. Do that *inside* a Validation and you have a half-applied function
        sitting in a container - which is precisely what ap is designed to consume.

        Implementation is a one-liner: it is map2 where the combining step is just "call it".
        */
        static <E, A, R> Validation<E, R> ap(Validation<E, Function<A, R>> vf, Validation<E, A> va) {
            return map2(vf, va, (f, a) -> f.apply(a));
        }

        /*
        map3, built from map + ap. Follow the types, one line at a time, for
        f = RegisterUserCommand::new, i.e. (String, String, Integer) -> RegisterUserCommand:

          step 1   a.map(curried)
                   `curried` turns f into String -> (String -> (Integer -> RegisterUserCommand)).
                   Mapping it over `a` applies only the first argument, leaving:
                       Validation<E, Function<String, Function<Integer, RegisterUserCommand>>>
                   A partially applied function, wrapped in a Validation. If `a` was Invalid, this is
                   just Invalid - and crucially, its errors are still there.

          step 2   ap(that, b)
                   Supplies the second argument and merges b's errors, leaving:
                       Validation<E, Function<Integer, RegisterUserCommand>>

          step 3   ap(that, c)
                   Supplies the last argument and merges c's errors, leaving:
                       Validation<E, RegisterUserCommand>

        Every ap merges error lists, so a failure at any of the three positions survives all the way to
        the end and arrives alongside the others. That is the whole mechanism - and it extends to map4,
        map5, mapN by adding more ap calls. Nothing new is needed.
        */
        static <E, A, B, C, R> Validation<E, R> map3(Validation<E, A> a, Validation<E, B> b,
                                                     Validation<E, C> c,
                                                     TriFn<A, B, C, R> f) {
            // Curry f by hand: one argument at a time, exactly as in Mod002.
            Function<A, Function<B, Function<C, R>>> curried =
                    va -> vb -> vc -> f.apply(va, vb, vc);

            Validation<E, Function<B, Function<C, R>>> afterA = a.map(curried);
            Validation<E, Function<C, R>>              afterB = ap(afterA, b);
            return                                              ap(afterB, c);
        }

        @FunctionalInterface interface TriFn<A, B, C, R> { R apply(A a, B b, C c); }
    }
    public record Valid<E, A>(A value)          implements Validation<E, A> {}
    public record Invalid<E, A>(List<E> errors) implements Validation<E, A> {}

    // =================================================================================================
    // Domain - a registration form with three independent fields
    // =================================================================================================

    record RegisterUserCommand(String email, String password, int age) {}

    private static final Pattern EMAIL_RE = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    // Each validator looks at exactly one field and knows nothing about the others. That independence is
    // what makes accumulation possible - and it is a design property of the validators, not of the type.
    static Validation<String, String> validateEmail(String email) {
        if (email == null || email.isBlank()) return Validation.invalid("email: must not be blank");
        if (!EMAIL_RE.matcher(email).matches()) return Validation.invalid("email: must look like a@b.c");
        return Validation.valid(email);
    }

    static Validation<String, String> validatePassword(String pwd) {
        if (pwd == null || pwd.length() < 8) return Validation.invalid("password: must be at least 8 characters");
        return Validation.valid(pwd);
    }

    static Validation<String, Integer> validateAge(int age) {
        if (age < 0 || age > 120) return Validation.invalid("age: must be between 0 and 120");
        return Validation.valid(age);
    }

    /** The three field validators combined into one whole-form validator. */
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
    Section 1 - why Either is not enough

    Chained with flatMap, Either stops at the first failure:

        validateEmail(input)
            .flatMap(email -> validatePassword(input))   // skipped entirely if the email was bad
            .flatMap(pwd   -> validateAge(input))        // skipped if either earlier step failed

    Look closely at why it must skip. flatMap's argument is a function `A -> Either<E, B>` - it needs an
    A to produce anything at all. When the receiver is a Left there is no A. The function cannot be
    called, so the second check cannot run, so its errors cannot exist. This is not a design decision
    that could be reversed; it follows from the shape of flatMap.

    For dependent steps that behaviour is correct and desirable. For a form it means the user fixes the
    email, resubmits, and only then learns the password is too short.

    The demo below runs the two checks separately to show that both failures are available in principle.
    Sections 2 and 3 show the machinery that actually keeps both.
    */
    static void whyEitherIsNotEnough() {
        System.out.println("[Section 1] why Either is not enough");

        var emailResult = validateEmail("not-an-email");
        var pwdResult   = validatePassword("short");

        System.out.println("  email check    -> " + emailResult);
        System.out.println("  password check -> " + pwdResult);
        System.out.println("  both failed, and both messages are useful to the user.");
        System.out.println("  Either.flatMap would have discarded the second: with no value from the");
        System.out.println("  first step, the function producing the second could never be called.");
    }

    /*
    Section 2 - Validation<E, A>

        sealed interface Validation<E, A> permits Valid, Invalid {}
        record Valid<E, A>(A value)          implements Validation<E, A> {}
        record Invalid<E, A>(List<E> errors) implements Validation<E, A> {}

    Structurally this is Either with one change: Invalid holds a List<E> instead of a single E. That is
    the entire type-level difference, and it is what gives two failures somewhere to go when they meet.

    map behaves exactly as it does on Either: it runs on Valid and passes Invalid through untouched. A
    single validator therefore looks and reads like an Either-returning one.

    Everything interesting happens when you *combine* two of them, which is Section 3.
    */
    static void validationDemo() {
        System.out.println("[Section 2] Validation");
        System.out.println("  validateEmail(\"\")      = " + validateEmail(""));
        System.out.println("  validateEmail(\"a@b.c\") = " + validateEmail("a@b.c"));
        System.out.println("  validateAge(-1)         = " + validateAge(-1));
        System.out.println("  valid.map(toUpper)      = " + validateEmail("a@b.c").map(String::toUpperCase));
    }

    /*
    Section 3 - the applicative pattern: map2, ap, mapN

    map2 is the primitive:

        Validation<E, R> result = map2(a, b, combine);

        both Valid    -> Valid(combine(a, b))
        one Invalid   -> that one's errors
        both Invalid  -> both error lists, concatenated

    ap is map2 specialised to the case where the first argument already holds a function, and mapN is
    ap applied N-1 times over a curried function. The full derivation is in the comment on map3 above -
    it is worth reading once with the types in front of you, because after that "applicative" stops
    being jargon and becomes a shape you can recognise.

    The name comes from what the pattern does: it lets you APPLY an N-argument function to N values that
    are each wrapped in some context, without unwrapping them. The context - here, "might be invalid" -
    is combined automatically along the way.

    This is not exotic. The same pattern is what you want whenever you have several independent wrapped
    values and one function that needs all of them:

        - several CompletableFutures and one function needing all the results (allOf, then combine),
        - several Optionals that must all be present,
        - several parsers whose results feed one constructor.

    The demo shows all three outcomes: everything valid, one field bad, and two fields bad producing two
    messages from a single call.
    */
    static void applicativePattern() {
        System.out.println("[Section 3] applicative - map2 / ap / map3");

        var emailOk = Validation.<String, String>valid("alice@example.com");
        var pwdOk   = Validation.<String, String>valid("p4ssw0rd");
        var ageOk   = Validation.<String, Integer>valid(30);
        System.out.println("  all three valid  -> " + Validation.map3(emailOk, pwdOk, ageOk,
                RegisterUserCommand::new));

        // map2 on its own, with one side failing.
        var combined2 = Validation.map2(validateEmail("bad"), validatePassword("p4ssw0rd"),
                (e, p) -> e + "/" + p);
        System.out.println("  map2, email bad  -> " + combined2);

        // Two independent failures meeting in one call - both survive.
        var combinedBoth = Validation.map2(validateEmail("bad"), validatePassword("short"),
                (e, p) -> e + "/" + p);
        System.out.println("  map2, both bad   -> " + combinedBoth);
        System.out.println("  ^ one call, two error messages. That is the whole point of the module.");
    }

    /*
    Section 4 - Functor, Applicative, Monad, and why Validation stops at the middle one

    The three abstractions, defined purely by which operations a type supports:

    - Functor      map(F<A>, A -> B) -> F<B>
                   "I can transform the value inside without touching the container."
                   Option, Either, Try, Validation, List, Stream, CompletableFuture - all functors.

    - Applicative  Functor, plus pure(A) -> F<A> and ap(F<A -> B>, F<A>) -> F<B>
                   "I can combine several INDEPENDENT wrapped values with one function."
                   Note what is absent: no step may depend on another's result.

    - Monad        Applicative, plus flatMap(F<A>, A -> F<B>) -> F<B>
                   "I can chain steps where each one decides what happens next."

    Each level is strictly more powerful than the one above. So why would a type deliberately stop short?

    Because for Validation, flatMap and accumulation are mutually exclusive. Consider what
    flatMap(f) must return when the receiver is Invalid. f has type A -> Validation<E, B>, and Invalid
    holds no A. So f cannot be called, so the second validation never happens, so its errors do not
    exist to be collected. Any flatMap you could write for Validation would short-circuit - and a
    short-circuiting Validation is just Either with a one-element list.

    Where does the extra power actually go? Into dependency. flatMap can decide *what to do next* based
    on the previous result; ap cannot, because both arguments are fixed before either is examined. That
    is the trade in one sentence: applicative gives up dependency between steps, and gets independence -
    which means every step runs, which means every error is available.

    Practical reading: if your steps are independent, use the applicative combinators and collect all
    the errors. If a later step needs an earlier step's value, you need a monad, and short-circuiting
    comes with it whether you want it or not.
    */
    static void abstractionsHierarchy() {
        System.out.println("[Section 4] Functor / Applicative / Monad");
        System.out.println("  Functor     = map                 (transform what is inside)");
        System.out.println("  Applicative = map + pure + ap     (combine INDEPENDENT wrapped values)");
        System.out.println("  Monad       = Applicative + flatMap (chain DEPENDENT steps)");
        System.out.println();
        System.out.println("  Validation is an Applicative but deliberately NOT a Monad.");
        System.out.println("  Proof sketch: flatMap(f) needs an A to call f with. An Invalid has no A,");
        System.out.println("  so f never runs, so the next validator never runs, so its errors never");
        System.out.println("  exist. Any lawful flatMap here would short-circuit - and a short-circuiting");
        System.out.println("  Validation is just Either. The missing power IS the feature.");
    }

    /*
    Section 5 - end-to-end form validation

    Validate a RegisterUserCommand(email, password, age) against three independent rules:

    - email    - non-blank and matching a small regex,
    - password - at least 8 characters,
    - age      - between 0 and 120.

    Four probes, chosen so that the error count rises one at a time and the accumulation is visible:

    1. all three fields valid           -> Valid, 0 errors
    2. one bad field (email)            -> Invalid, 1 error
    3. two bad fields (email + age)     -> Invalid, 2 errors
    4. all three bad                    -> Invalid, 3 errors

    Probe 4 is the one that would be impossible with Either, which could only ever report the first.
    Each probe prints its messages so you can read what a user would actually be shown.
    */
    static void endToEnd() {
        System.out.println("[Section 5] form validation - assertions");

        record Probe(String label, RegisterUserCommand input, int expectedErrors) {}
        var probes = List.of(
                new Probe("all valid",           new RegisterUserCommand("alice@example.com", "p4ssw0rd", 30),   0),
                new Probe("invalid email",       new RegisterUserCommand("not-an-email",      "p4ssw0rd", 30),   1),
                new Probe("invalid email + age", new RegisterUserCommand("not-an-email",      "p4ssw0rd", -1),   2),
                new Probe("all three invalid",   new RegisterUserCommand("",                  "short",    200),  3));

        boolean allOk = true;
        for (var p : probes) {
            var result = validate(p.input);
            int errors = result instanceof Invalid<String, RegisterUserCommand>(List<String> es) ? es.size() : 0;
            boolean ok = errors == p.expectedErrors;
            if (!ok) allOk = false;
            System.out.printf(Locale.ROOT, "  [%-22s] %d error(s) (expected %d) %s%n",
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
