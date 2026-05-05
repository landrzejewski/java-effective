package pl.training.dsl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/*
What is a DSL

- A Domain-Specific Language lets the user write programs in vocabulary that closely matches the problem domain. Done
  well, it makes valid programs short and invalid programs unrepresentable.
- Internal DSLs are written in the host language (Java here). They get the host's syntax, IDE support, and types — at
  the cost of some syntactic noise.
- External DSLs have their own grammar and parser (e.g., HCL, GraphQL). They look cleaner but require tooling
  (parser, error reporting, IDE plugin).
- Java has no language-level "DSL syntax" (no Kotlin-style receivers, no Scala implicits). Idiomatic Java DSLs are
  built from fluent interfaces, lambdas as receivers, type-state, and AST + interpreter.
- The seven modules after this one each deepen one of those techniques on a real-world target (HTML, SQL, REST,
  validation, FSM, parser, type-state builder).
*/

final class DateRange {
    private final LocalDate from, to;
    private DateRange(LocalDate from, LocalDate to) { this.from = from; this.to = to; }

    static DateRange from(LocalDate from)            { return new DateRange(from, null); }
    DateRange to(LocalDate to)                       { return new DateRange(this.from, to); }

    @Override public String toString() { return "[" + from + " ... " + to + "]"; }
}

interface State {}
interface SizeMissing  extends State {}
interface SizePresent  extends State {}

final class Pizza<S extends State> {
    private final String size;
    private final List<String> toppings;

    private Pizza(String size, List<String> toppings) { this.size = size; this.toppings = toppings; }

    static Pizza<SizeMissing> start() { return new Pizza<>(null, List.of()); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    Pizza<SizePresent> size(String s) {
        // The runtime payload is identical; we only flip the phantom type parameter.
        return (Pizza<SizePresent>) (Pizza) new Pizza<>(s, this.toppings);
    }

    Pizza<S> topping(String t) {
        var next = new ArrayList<>(this.toppings); next.add(t);
        return new Pizza<>(this.size, List.copyOf(next));
    }

    // build() compiles only on Pizza<SizePresent>
    @SuppressWarnings("unused")
    String build() {
        // The next line will not compile if S is SizeMissing — see Mod008 §5 for the trick.
        return "Pizza(" + size + ", toppings=" + toppings + ")";
    }
}

sealed interface MiniExpr permits Lit, Add, Mul {}
record Lit(int value)             implements MiniExpr {}
record Add(MiniExpr a, MiniExpr b) implements MiniExpr {}
record Mul(MiniExpr a, MiniExpr b) implements MiniExpr {}

public final class Mod001DslPatternsAndDesign {

    private Mod001DslPatternsAndDesign() {}

    /*
    Method chaining (fluent interface)

    - Each call returns this (for mutable builders) or a new instance (for immutable ones). Reads top-to-bottom.
    - Mutable chaining is concise; immutable chaining is thread-safe and lets you save intermediate states.
    - The naming convention is verb-based: from(...), where(...), limit(...) — the chain reads like a sentence.
    - Pitfall: a single chain that sets every parameter regardless of order ("telescoping builder") makes invalid
      combinations representable. The fixes are type-state (Mod008) or factory entry methods: only the factory
      provides an entry into the chain.
    */
    static void fluentInterface() {
        System.out.println("[Section 2] fluent interface");

        var range = DateRange.from(LocalDate.of(2026, 1, 1)).to(LocalDate.of(2026, 12, 31));
        System.out.println("  range = " + range);

        // Compare with an old-style "telescoping" constructor — every reader has to
        // remember the parameter order. The factory + chain reads itself.
    }

    /*
    Lambda receivers ("with-this" blocks)

    - Kotlin has explicit "receiver" lambdas — inside the block, this is the builder. Java does not, but passing
      Consumer<Builder> lambdas approximates it: the user writes b -> b.x().y() instead of this.x().y().
    - The convention: a top-level static or instance entry method takes the lambda, creates a builder, runs the
      lambda on it, and returns the result.
    - Nested structure follows the same pattern: a method on the outer builder that accepts another
      Consumer<ChildBuilder> and creates a child.
    - This is the technique used in Mod002 (HTML) — every nesting level passes the inner builder into a lambda.
    */
    static void lambdaReceivers() {
        System.out.println("[Section 3] lambda receivers");

        // Tiny fluent receiver: `tag(name, b -> ...)` configures the inner builder.
        String html = tag("ul", ul -> {
            ul.append(tag("li", li -> li.text("first")));
            ul.append(tag("li", li -> li.text("second")));
        });
        System.out.println("  " + html);
    }

    // toy receiver builder used only in §3
    static String tag(String name, Consumer<TagBuilder> body) {
        var b = new TagBuilder();
        body.accept(b);
        return "<" + name + ">" + b + "</" + name + ">";
    }

    static final class TagBuilder {
        private final StringBuilder buf = new StringBuilder();
        void append(String html)   { buf.append(html); }
        void text(String s)        { buf.append(escape(s)); }
        @Override public String toString() { return buf.toString(); }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /*
    Static factory + instance method

    - Start the chain with a static factory whose name matches the domain verb: query(), request(), validate(). Avoid
      calling new from user code.
    - The factory returns the most general entry point of the DSL; the user can only walk forward from there.
    - Pairs naturally with type-state (Mod008) — the factory returns the "empty" type-state, and each subsequent
      method advances the state.
    */
    static void staticFactoryEntry() {
        System.out.println("[Section 4] static factory entry");

        // The factory `where` is the *only* way to start; readers cannot construct
        // a Predicate<Integer> through some other backdoor and break the abstraction.
        Predicate<Integer> rule = Mod001DslPatternsAndDesign.<Integer>where(n -> n > 0).and(n -> n < 100);
        System.out.println("  rule.test(50)  = " + rule.test(50));
        System.out.println("  rule.test(-1)  = " + rule.test(-1));
        System.out.println("  rule.test(200) = " + rule.test(200));
    }

    static <T> Predicate<T> where(Predicate<T> p) { return p; }

    /*
    Compile-time enforcement (phantom types preview)

    - A phantom type is a type parameter that does not appear in the runtime state — it only exists in the type
      system to encode build progress.
    - Example: a Builder<Required, Built> whose Required and Built are each one of Missing or Present. The build()
      method is declared only on the fully-Present parameterisation.
    - Mod008 covers this in depth on an HTTP request builder. The preview below shows the simplest form on a 2-state
      pizza-ordering builder.
    */
    static void phantomTypesPreview() {
        System.out.println("[Section 5] phantom types preview");

        var ready = Pizza.start().topping("cheese").size("large").topping("ham");
        System.out.println("  built = " + ready.build());

        // Pizza.start().topping("cheese").build();  // would not compile — size missing
    }

    /*
    Code-as-data (AST + interpreter)

    - Instead of executing operations as the user types them, the DSL builds an immutable tree (the abstract syntax
      tree). Behaviour is added later by a separate evaluator.
    - Pay-off: the same AST can be optimised, serialised, transformed, displayed, type-checked, or run in different
      evaluators (interpret vs. compile to SQL, for instance).
    - Mod003 (SQL) and Mod007 (parser combinators) both use this pattern. The preview below evaluates a 3-node
      arithmetic AST.
    */
    static void astInterpreterPreview() {
        System.out.println("[Section 6] AST + interpreter");

        MiniExpr ast = new Mul(new Add(new Lit(1), new Lit(2)), new Lit(3));
        System.out.println("  ast    = " + ast);
        System.out.println("  result = " + eval(ast));
    }

    static int eval(MiniExpr e) {
        return switch (e) {
            case Lit(int v) -> v;
            case Add(MiniExpr a, MiniExpr b) -> eval(a) + eval(b);
            case Mul(MiniExpr a, MiniExpr b) -> eval(a) * eval(b);
        };
    }

    /*
    Library exemplars (real-world DSLs to study)

    - AssertJ — fluent assertions with type-aware predicates
      (assertThat(x).isInstanceOf(Foo.class).extracting(...).contains(...)).
    - Mockito — when(x.foo()).thenReturn(y) builds a recording AST then injects behaviour. Mod008 phantom-type
      technique.
    - jOOQ — typed SQL DSL with Column<T>. Mod003 is jOOQ-light.
    - Javalin / Spark — REST routing DSL. Mod004 is Javalin-light.
    - Spring State Machine / Akka FSM — workflow modelling. Mod006 is state-machine-light.
    - parsec / fastparse / Combine — parser combinator libraries from Haskell, Scala, Swift. Mod007 ports the idea to
      Java.
    - Hibernate Validator — declarative bean validation. Mod005 is the programmatic builder version of the same idea.

    A good DSL borrows visibly from these — the more your DSL feels like something the user already knows, the lower
    the learning cost.
    */
    static void libraryExemplars() {
        System.out.println("[Section 7] library exemplars (study list)");
        List<String> exemplars = List.of(
                "AssertJ        — fluent assertions",
                "Mockito        — recording DSL + verification",
                "jOOQ           — typed SQL (see Mod003)",
                "Javalin/Spark  — REST routing (see Mod004)",
                "Spring SM      — state machines (see Mod006)",
                "parsec/parsec4j— parser combinators (see Mod007)",
                "Hibernate Val. — declarative validation (see Mod005)");
        exemplars.forEach(line -> System.out.println("  " + line));
    }

    public static void main(String[] args) {
        fluentInterface();
        lambdaReceivers();
        staticFactoryEntry();
        phantomTypesPreview();
        astInterpreterPreview();
        libraryExemplars();
        System.out.println("Mod001DslPatternsAndDesign finished");
    }

    @SuppressWarnings("unused")
    private static <T, U> Function<T, U> never() { return null; }
}
