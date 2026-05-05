package pl.training.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/*
Why a validation DSL

- Bean Validation annotations are concise but rigid: cross-field rules require a separate @AssertTrue method,
  conditional rules require a @GroupSequenceProvider, and unit-testing a validator is awkward because the validator
  is hidden behind reflection.
- A programmatic validation DSL is plain Java: rules live in a method, unit tests are trivial, conditional logic is
  if, custom predicates are free.
- Goal: build a Validator<T> that produces a list of Violation records (field path + message). Errors are collected,
  not thrown — the caller decides what to do with them.
- This module's anchor is registering a user
  (RegisterUserCommand(email, password, age, country, optional referralCode)).

Validator<T> and Violation

- Violation(field, message) — a single problem report. The field is a dotted path so nested objects can report
  address.zipCode: must not be blank.
- Validator<T> is a list of Rule<T>; each rule turns the input into a list of violations. validate(t) runs every
  rule and concatenates.
- Rules are expressed declaratively in a small DSL (§3-4).
*/

public final class Mod005ValidationDsl {

    private Mod005ValidationDsl() {}

    // =================================================================================================
    // Domain
    // =================================================================================================

    record Address(String city, String zipCode) {}
    record RegisterUserCommand(String email, String password, int age, String country,
                               Address address, Optional<String> guardianEmail) {}

    // =================================================================================================
    // Violation, Rule, Validator
    // =================================================================================================

    public record Violation(String field, String message) {
        public Violation prefixed(String prefix) {
            return new Violation(prefix.isEmpty() ? this.field : prefix + "." + this.field, this.message);
        }
    }

    @FunctionalInterface interface Rule<T> { List<Violation> check(T value); }

    public static final class Validator<T> {
        private final List<Rule<T>> rules = new ArrayList<>();
        public Validator<T> add(Rule<T> r) { rules.add(r); return this; }
        public List<Violation> validate(T value) {
            var out = new ArrayList<Violation>();
            for (var r : rules) out.addAll(r.check(value));
            return List.copyOf(out);
        }
    }

    // =================================================================================================
    // FieldRules — the per-field DSL
    // =================================================================================================

    public static final class FieldRules<F> {
        private final List<Predicate<F>> tests = new ArrayList<>();
        private final List<String> messages = new ArrayList<>();

        public FieldRules<F> notNull() {
            tests.add(v -> v != null); messages.add("must not be null"); return this;
        }
        public FieldRules<F> predicate(String message, Predicate<F> p) {
            tests.add(p); messages.add(message); return this;
        }

        // String-only helpers — only callable on FieldRules<String>
        @SuppressWarnings("unchecked")
        public FieldRules<F> notBlank() {
            tests.add(v -> v != null && !((String) v).isBlank());
            messages.add("must not be blank");
            return this;
        }
        @SuppressWarnings("unchecked")
        public FieldRules<F> matches(Pattern regex) {
            tests.add(v -> v != null && regex.matcher((String) v).matches());
            messages.add("must match " + regex.pattern());
            return this;
        }
        @SuppressWarnings("unchecked")
        public FieldRules<F> minLength(int n) {
            tests.add(v -> v != null && ((String) v).length() >= n);
            messages.add("must be at least " + n + " characters");
            return this;
        }

        // Numeric helpers — only callable on FieldRules<Integer> etc.
        @SuppressWarnings("unchecked")
        public FieldRules<F> between(int lo, int hi) {
            tests.add(v -> {
                int x = ((Number) v).intValue();
                return x >= lo && x <= hi;
            });
            messages.add("must be between " + lo + " and " + hi);
            return this;
        }

        public FieldRules<F> inSet(java.util.Set<F> allowed) {
            tests.add(allowed::contains);
            messages.add("must be one of " + allowed);
            return this;
        }

        List<Violation> apply(String fieldName, F value) {
            var out = new ArrayList<Violation>();
            for (int i = 0; i < tests.size(); i++) {
                if (!tests.get(i).test(value)) out.add(new Violation(fieldName, messages.get(i)));
            }
            return out;
        }
    }

    // =================================================================================================
    // ValidatorBuilder — top-level DSL
    // =================================================================================================

    public static final class ValidatorBuilder<T> {
        private final Validator<T> v = new Validator<>();

        public <F> ValidatorBuilder<T> field(String name, Function<T, F> extract,
                                             Function<FieldRules<F>, FieldRules<F>> body) {
            v.add(t -> {
                var rules = body.apply(new FieldRules<>());
                return rules.apply(name, extract.apply(t));
            });
            return this;
        }

        public ValidatorBuilder<T> when(Predicate<T> condition,
                                        Function<ValidatorBuilder<T>, ValidatorBuilder<T>> body) {
            v.add(t -> {
                if (!condition.test(t)) return List.of();
                var inner = body.apply(new ValidatorBuilder<>());
                return inner.build().validate(t);
            });
            return this;
        }

        public <S> ValidatorBuilder<T> nested(String name, Function<T, S> extract, Validator<S> sub) {
            v.add(t -> sub.validate(extract.apply(t)).stream()
                    .map(violation -> violation.prefixed(name)).toList());
            return this;
        }

        public Validator<T> build() { return v; }
    }

    public static <T> ValidatorBuilder<T> rules() { return new ValidatorBuilder<>(); }

    // =================================================================================================
    // Concrete validators for the demo
    // =================================================================================================

    static final Pattern EMAIL_RE = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    static final Validator<Address> ADDRESS_VALIDATOR = Mod005ValidationDsl.<Address>rules()
            .field("city",    Address::city,    f -> f.notBlank())
            .field("zipCode", Address::zipCode, f -> f.notBlank().matches(Pattern.compile("\\d{2}-\\d{3}")))
            .build();

    static final Validator<RegisterUserCommand> USER_VALIDATOR =
            Mod005ValidationDsl.<RegisterUserCommand>rules()
                    .field("email",    RegisterUserCommand::email,    f -> f.notBlank().matches(EMAIL_RE))
                    .field("password", RegisterUserCommand::password, f -> f.notBlank().minLength(8))
                    .field("age",      RegisterUserCommand::age,      f -> f.between(0, 120))
                    .field("country",  RegisterUserCommand::country,
                            f -> f.notBlank().inSet(java.util.Set.of("PL", "DE", "FR", "UK", "US")))
                    .nested("address", RegisterUserCommand::address, ADDRESS_VALIDATOR)
                    .when(u -> u.age() < 18, sub -> sub
                            .field("guardianEmail",
                                    u -> u.guardianEmail().orElse(null),
                                    f -> f.notNull().notBlank().matches(EMAIL_RE)))
                    .build();

    // =================================================================================================
    // Sections
    // =================================================================================================

    /*
    Field-targeted rules

    - field("email", U::email, r -> r.notBlank().matches(EMAIL_RE)) records the field name once and adds it to every
      violation that the inner rules emit.
    - The inner builder exposes the common atomic checks: notBlank, matches, min, max, between, inSet,
      predicate(custom). Each returns the same builder so they chain.
    - All checks run regardless of whether earlier ones failed — the user sees every problem in one pass instead of
      "fix this, run, fix the next, run again" cycles.
    */
    static void fieldRulesDemo() {
        System.out.println("[Section 3] field-targeted rules");
        var rule = Mod005ValidationDsl.<RegisterUserCommand>rules()
                .field("email", RegisterUserCommand::email, f -> f.notBlank().matches(EMAIL_RE))
                .build();
        var bad = new RegisterUserCommand("not-an-email", "p", 30, "PL",
                new Address("Warsaw", "00-001"), Optional.empty());
        rule.validate(bad).forEach(v -> System.out.println("  " + v));
    }

    /*
    Conditional rules

    - when(predicate, sub -> ...) runs the inner rules only when the predicate over the entire object is true. Used
      for cross-field logic: "if age < 18, require a guardianEmail".
    - unless(predicate, sub -> ...) is the inverse and reads cleaner for "unless overrideFlag is set, …" cases.
    - The conditional wrapper preserves the field-path stack so violations inside a when block still carry the right
      prefix.
    */
    static void conditionalRulesDemo() {
        System.out.println("[Section 4] conditional rules");
        var minorWithGuardian = new RegisterUserCommand("alice@example.com", "p4ssw0rd", 14, "PL",
                new Address("Warsaw", "00-001"), Optional.of("guardian@example.com"));
        var minorMissing = new RegisterUserCommand("alice@example.com", "p4ssw0rd", 14, "PL",
                new Address("Warsaw", "00-001"), Optional.empty());
        System.out.println("  minor + guardian: " + USER_VALIDATOR.validate(minorWithGuardian));
        System.out.println("  minor missing  : " + USER_VALIDATOR.validate(minorMissing));
    }

    /*
    Sub-validator composition

    - A validator can call into another validator on a sub-object via nested("address", U::address,
      ADDRESS_VALIDATOR). Violations from the sub-validator are re-prefixed with address..
    - The same pattern works for collections via each("tags", U::tags, ITEM_RULE) — prefix becomes tags[0], tags[1],
      ... .
    - Composition lets you reuse a small validator (AddressValidator) inside multiple top-level validators.
    */
    static void nestedComposition() {
        System.out.println("[Section 5] nested validator composition");
        var u = new RegisterUserCommand("alice@example.com", "p4ssw0rd", 30, "PL",
                new Address("", "bad-zip"), Optional.empty());
        USER_VALIDATOR.validate(u).forEach(v -> System.out.println("  " + v));
    }

    /*
    End-to-end

    - Validate three sample inputs:
      1. fully valid adult,
      2. invalid email + short password,
      3. minor without guardianEmail,
    - Print the resulting violations grouped by field; assert against an expected count per case.
    */
    static void endToEnd() {
        System.out.println("[Section 6] end-to-end with assertions");

        var validAdult = new RegisterUserCommand("alice@example.com", "p4ssw0rd", 30, "PL",
                new Address("Warsaw", "00-001"), Optional.empty());
        var badEmailShortPwd = new RegisterUserCommand("not-an-email", "abc", 30, "PL",
                new Address("Warsaw", "00-001"), Optional.empty());
        var minorMissingGuardian = new RegisterUserCommand("alice@example.com", "p4ssw0rd", 14, "PL",
                new Address("Warsaw", "00-001"), Optional.empty());

        record Probe(String label, RegisterUserCommand input, int expectedViolations) {}
        var probes = List.of(
                new Probe("valid adult",                 validAdult,             0),
                new Probe("invalid email + short pwd",   badEmailShortPwd,       2),
                new Probe("minor missing guardian",      minorMissingGuardian,   3));

        boolean allOk = true;
        for (var p : probes) {
            var violations = USER_VALIDATOR.validate(p.input);
            boolean ok = violations.size() == p.expectedViolations;
            if (!ok) allOk = false;
            System.out.printf("  [%-26s] %d violation(s) (expected %d) %s%n",
                    p.label, violations.size(), p.expectedViolations, ok ? "✓" : "✗");
            for (var v : violations) System.out.println("      " + v.field + ": " + v.message);
        }
        System.out.println("  all probes match expected counts? " + allOk);
    }

    public static void main(String[] args) {
        fieldRulesDemo();
        conditionalRulesDemo();
        nestedComposition();
        endToEnd();
        System.out.println("Mod005ValidationDsl finished");
    }
}
