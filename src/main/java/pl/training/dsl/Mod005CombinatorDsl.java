package pl.training.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/*
Combinators — behaviour as composable values

- A combinator DSL has no builder and no tree. There is one interface describing a unit of behaviour, a few
  factories producing the primitive ones, and a few default methods that combine two into a bigger one of the
  same type. The vocabulary is closed, so anything the user writes is again a value of that type.
- The pay-off is that composition is total: every rule combines with every other rule, no registration step is
  needed, a rule can be stored in a constant, passed as an argument, or unit-tested on its own. Streams,
  Predicate.and/or, Comparator.thenComparing and RxJava are all this shape.
- Anchor here: validating a registration command. Rule<T> reports problems instead of throwing, so one pass
  tells the user everything that is wrong rather than making them fix errors one round-trip at a time.
- Contrast with Bean Validation annotations: those are terser for the simple cases but rigid — a cross-field
  rule needs @AssertTrue, a conditional rule needs a group-sequence provider, and testing means going through
  reflection. Rules as values are plain Java: `if` is `if`, and a test just calls check().

Getting the typing right is the whole exercise

- The tempting design is one FieldRules<F> class carrying every helper (notBlank, minLength, between, ...) and
  casting internally. It reads well and it is a trap: notBlank() would be offered on a numeric field and blow
  up with a ClassCastException at validation time — the exact failure mode a DSL exists to prevent.
- So the element type belongs in each factory's signature instead: notBlank() *is* a Rule<String>, between()
  *is* a Rule<Integer>. Applying the wrong one to a field then fails to compile, and not one cast is needed
  anywhere in this file.
*/

public final class Mod005CombinatorDsl {

    private Mod005CombinatorDsl() {}

    // =================================================================================================
    // Domain
    // =================================================================================================

    record Address(String city, String zipCode) {}

    record RegisterUser(String email, String password, int age, String country,
                        Address address, Optional<String> guardianEmail) {}

    // =================================================================================================
    // Violation and Rule
    // =================================================================================================

    /** One problem report. `field` is a dotted path, so nested objects report address.zipCode. */
    public record Violation(String field, String message) {

        Violation under(String prefix) {
            return new Violation(field.isEmpty() ? prefix : prefix + "." + field, message);
        }

        @Override public String toString() { return field.isEmpty() ? message : field + ": " + message; }
    }

    /*
    Rule<T> is the single type of the DSL. It is a @FunctionalInterface, so every primitive rule below is a
    lambda, and the combinators are default methods that return another Rule<T> — which is what makes the
    vocabulary closed.
    */
    @FunctionalInterface
    public interface Rule<T> {

        List<Violation> check(T value);

        /** Both rules always run, so a single pass reports every problem. */
        default Rule<T> and(Rule<T> other) {
            return value -> {
                var all = new ArrayList<>(check(value));
                all.addAll(other.check(value));
                return List.copyOf(all);
            };
        }

        /** Applies only when the condition holds — cross-field logic without leaving the DSL. */
        default Rule<T> when(Predicate<T> condition) {
            return value -> condition.test(value) ? check(value) : List.of();
        }
    }

    static <T> Rule<T> noRules() { return value -> List.of(); }

    // =================================================================================================
    // Primitive rules — the element type lives in the signature, so misuse cannot compile
    // =================================================================================================

    private static <T> Rule<T> rule(String message, Predicate<T> ok) {
        return value -> ok.test(value) ? List.of() : List.of(new Violation("", message));
    }

    static <T> Rule<T> notNull() {
        return rule("must not be null", value -> value != null);
    }
    static Rule<String> notBlank() {
        return rule("must not be blank", value -> value != null && !value.isBlank());
    }
    static Rule<String> minLength(int n) {
        return rule("must be at least " + n + " characters", value -> value != null && value.length() >= n);
    }
    static Rule<String> matches(Pattern pattern) {
        return rule("must match " + pattern.pattern(), value -> value != null && pattern.matcher(value).matches());
    }
    static Rule<Integer> between(int low, int high) {
        return rule("must be between " + low + " and " + high,
                value -> value != null && value >= low && value <= high);
    }
    /** Null-safe on purpose: a missing value is a violation to report, never a NullPointerException. */
    static <T> Rule<T> inSet(Set<T> allowed) {
        return rule("must be one of " + allowed, value -> value != null && allowed.contains(value));
    }

    // =================================================================================================
    // Lifting — turning a rule about a field into a rule about the whole object
    // =================================================================================================

    /*
    `on` is the only structural combinator: it reads one field out of the object, runs a rule about that
    field's type, and prefixes whatever comes back with the field name. This is where the typing pays off —
    the extractor fixes F, so the rule argument must be a Rule<F> and nothing else.

    Because it prefixes rather than overwrites, one combinator covers both cases. A primitive rule reports
    under the empty path, so it comes back as "email". A whole sub-validator reports "zipCode", so nesting it
    under "address" comes back as "address.zipCode" — no separate `nested` operation is needed.
    */
    static <T, F> Rule<T> on(String field, Function<T, F> extract, Rule<F> rule) {
        return value -> rule.check(extract.apply(value)).stream()
                .map(violation -> violation.under(field))
                .toList();
    }

    /** Combines any number of rules about the same type; the identity is the rule that reports nothing. */
    @SafeVarargs
    static <T> Rule<T> all(Rule<T>... rules) {
        Rule<T> combined = noRules();
        for (var rule : rules) combined = combined.and(rule);
        return combined;
    }

    // =================================================================================================
    // The validators for the demo — ordinary constants, because rules are ordinary values
    // =================================================================================================

    static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    static final Rule<Address> ADDRESS_RULES = all(
            on("city",    Address::city,    notBlank()),
            on("zipCode", Address::zipCode, notBlank().and(matches(Pattern.compile("\\d{2}-\\d{3}")))));

    static final Rule<RegisterUser> USER_RULES = all(
            on("email",    RegisterUser::email,    notBlank().and(matches(EMAIL))),
            on("password", RegisterUser::password, notBlank().and(minLength(8))),
            on("age",      RegisterUser::age,      between(0, 120)),
            on("country",  RegisterUser::country,  inSet(Set.of("PL", "DE", "FR", "UK", "US"))),
            on("address", RegisterUser::address, ADDRESS_RULES),
            on("guardianEmail",
                    (RegisterUser u) -> u.guardianEmail().orElse(null),
                    Mod005CombinatorDsl.<String>notNull().and(matches(EMAIL)))
                    .when(user -> user.age() < 18));

    // =================================================================================================
    // Sections
    // =================================================================================================

    /*
    Primitive rules are values

    - Each factory returns a Rule of a specific type. A rule can be named, stored, reused and tested without
      any object to validate — it is data about a check, not a check in progress.
    - `and` runs both sides unconditionally, which is why the report below lists two problems for one value
      rather than stopping at the first.
    */
    static void rulesAreValues() {
        System.out.println("[Section 1] primitive rules are values");

        Rule<String> password = notBlank().and(minLength(8));
        System.out.println("  check(\"\")         = " + password.check(""));
        System.out.println("  check(\"abc\")      = " + password.check("abc"));
        System.out.println("  check(\"p4ssw0rd\") = " + password.check("p4ssw0rd"));
    }

    /*
    The typing is real

    - notBlank() is a Rule<String>; between(...) is a Rule<Integer>. `on` derives the field type from the
      extractor, so pairing the wrong rule with a field is a compile error, not a run-time surprise:
        on("age",   RegisterUser::age,   notBlank())   Rule<String> where Rule<Integer> is required
        on("email", RegisterUser::email, between(0,1)) Rule<Integer> where Rule<String> is required
    - Nothing in this file casts. The one annotation, @SafeVarargs on all(...), is an assertion that the
      method never writes to its varargs array - not a cast papering over a type the design got wrong. That
      is the sign the types are carrying the design rather than decorating it.
    */
    static void typingIsReal() {
        System.out.println("[Section 2] the typing is real");

        System.out.println("  " + on("age", RegisterUser::age, between(0, 120))
                .check(sample(150, "alice@example.com")));
        System.out.println("  on(\"age\", RegisterUser::age, notBlank())  // rejected at compile time");
    }

    /*
    Composing rules about the whole object

    - `on` lifts a rule about a field into a rule about the object (and composes the path, so it nests),
      `when` makes a rule conditional on the object as a whole, and `all` combines any number of them.
    - The guardian-e-mail rule below is the cross-field case: it only applies to minors, which in an annotation
      world needs a custom validator and in this one is a call to when(...).
    */
    static void composing() {
        System.out.println("[Section 3] composing rules about the whole object");

        var adult = new RegisterUser("alice@example.com", "p4ssw0rd", 30, "PL",
                new Address("Warsaw", "00-001"), Optional.empty());
        var minor = new RegisterUser("alice@example.com", "p4ssw0rd", 14, "PL",
                new Address("Warsaw", "00-001"), Optional.empty());

        System.out.println("  adult, no guardian -> " + USER_RULES.check(adult));
        System.out.println("  minor, no guardian -> " + USER_RULES.check(minor));

        var badAddress = new RegisterUser("alice@example.com", "p4ssw0rd", 30, "PL",
                new Address("", "bad-zip"), Optional.empty());
        System.out.println("  nested paths       -> " + USER_RULES.check(badAddress));
    }

    /*
    End-to-end, with checks

    - Four inputs, each with a known number of problems, and the count is asserted. The important property is
      that the second case reports *both* the bad e-mail and the short password: nothing short-circuits.
    - A missing country reports "must be one of ..." rather than throwing, because inSet is null-safe. A rule
      that throws on bad input is a rule that cannot report on bad input.
    */
    static void endToEnd() {
        System.out.println("[Section 4] end-to-end with expected counts");

        record Probe(String label, RegisterUser input, int expected) {}

        var probes = List.of(
                new Probe("valid adult",
                        sample(30, "alice@example.com"), 0),
                new Probe("bad e-mail + short password",
                        new RegisterUser("not-an-email", "abc", 30, "PL",
                                new Address("Warsaw", "00-001"), Optional.empty()), 2),
                new Probe("minor without guardian",
                        new RegisterUser("alice@example.com", "p4ssw0rd", 14, "PL",
                                new Address("Warsaw", "00-001"), Optional.empty()), 2),
                new Probe("null country",
                        new RegisterUser("alice@example.com", "p4ssw0rd", 30, null,
                                new Address("Warsaw", "00-001"), Optional.empty()), 1));

        boolean allOk = true;
        for (var probe : probes) {
            var violations = USER_RULES.check(probe.input());
            boolean ok = violations.size() == probe.expected();
            allOk &= ok;
            System.out.printf(Locale.ROOT, "  [%-28s] %d problem(s), expected %d  %s%n",
                    probe.label(), violations.size(), probe.expected(), ok ? "ok" : "MISMATCH");
            violations.forEach(violation -> System.out.println("      " + violation));
        }
        System.out.println("  all probes match? " + allOk);
    }

    static RegisterUser sample(int age, String email) {
        return new RegisterUser(email, "p4ssw0rd", age, "PL",
                new Address("Warsaw", "00-001"), Optional.empty());
    }

    /*
    The same shape elsewhere

    - Nothing above is specific to validation. Swap "a value in, a list of problems out" for "a value in, the
      next value out" and the identical set of combinators builds a task pipeline: a Step<I, O> with andThen,
      when and parallel, composed the same way and run the same way.
    - The recognisable signs that a combinator DSL is the right tool: the units are independent, they compose
      associatively, and there is a sensible do-nothing unit (noRules() here) to start a fold from.
    */
    static void sameShapeElsewhere() {
        System.out.println("[Section 5] the same shape elsewhere");
        System.out.println("  Rule<T>       : T -> List<Violation>   and / when / on   (this module)");
        System.out.println("  Predicate<T>  : T -> boolean           and / or / negate (java.util.function)");
        System.out.println("  Parser<T>     : String -> Result<T>    and / or / many   (Mod006)");
        System.out.println("  Step<I,O>     : I -> Outcome<O>        andThen / when    (a task pipeline)");
    }

    public static void main(String[] args) {
        rulesAreValues();
        typingIsReal();
        composing();
        endToEnd();
        sameShapeElsewhere();
        System.out.println("Mod005CombinatorDsl finished");
    }
}
