package pl.training.dsl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/*
What a DSL is, and what Java gives you to build one

- A Domain-Specific Language lets the caller write in the vocabulary of the problem domain. Done well it makes
  valid programs short and invalid programs unrepresentable.
- Internal DSLs are written in the host language (Java here). They inherit its syntax, IDE support and type
  checking, at the cost of some syntactic noise. External DSLs (SQL, GraphQL, HCL) have their own grammar and
  need their own parser and error reporting — Mod006 builds one.
- Java has no language-level DSL support: no Kotlin receivers, no Scala implicits, no extension methods.
  Idiomatic Java DSLs are assembled from four plain-language mechanisms, one per section below.
- The five modules after this one each take one mechanism to its useful conclusion:
    Mod002 — nested builders and safety by construction (HTML)
    Mod003 — type-state / phantom types (a builder that cannot forget a field)
    Mod004 — code as data: one sealed AST, several interpreters (SQL)
    Mod005 — combinators: behaviour as composable values (validation)
    Mod006 — parser combinators: an external DSL, text to AST to value
*/

public final class Mod001DslBasics {

    private Mod001DslBasics() {}

    /*
    Method chaining — mutable versus immutable

    - Each call returns a receiver so the next call can follow the dot. Two ways to do it:
      mutable  — mutate fields and `return this`. One object, cheapest, but the chain is a sequence of side
                 effects: you cannot branch off a half-built value and reuse it.
      immutable — `return new ...` with the changed field. Every intermediate is a usable, shareable value, so
                 a partially configured object can be a constant reused by many call sites. Costs one
                 allocation per step, which is irrelevant at configuration-time volumes.
    - Prefer immutable unless the chain is hot; the reuse property is what makes a DSL feel like a language
      rather than a script.
    - Verb-based names make the chain read as a sentence: from(...).to(...), not setFrom(...).setTo(...).
    */
    static void methodChaining() {
        System.out.println("[Section 1] method chaining — mutable vs immutable");

        // Immutable: `q1` is still usable after `q2` is derived from it.
        var q1 = Query.select("id", "total").from("orders");
        var q2 = q1.limit(10);
        var q3 = q1.limit(100);
        System.out.println("  q1 = " + q1);
        System.out.println("  q2 = " + q2 + "   (derived from q1)");
        System.out.println("  q3 = " + q3 + "   (q1 unchanged, reused)");

        // Mutable: one object threaded through the chain — the intermediate states are gone.
        var log = new LogLine().at("12:00").level("WARN").message("disk almost full");
        System.out.println("  mutable chain = " + log);
    }

    /** Immutable fluent value: every step returns a new instance, so intermediates stay usable. */
    record Query(List<String> columns, String table, int limit) {

        static Query select(String... columns) { return new Query(List.of(columns), null, -1); }

        Query from(String table) { return new Query(columns, table, limit); }
        Query limit(int n)       { return new Query(columns, table, n); }

        @Override public String toString() {
            return "SELECT " + String.join(", ", columns)
                    + (table == null ? "" : " FROM " + table)
                    + (limit < 0 ? "" : " LIMIT " + limit);
        }
    }

    /** Mutable fluent builder: every step returns `this`. */
    static final class LogLine {
        private String time = "?", level = "INFO", message = "";

        LogLine at(String time)          { this.time = time; return this; }
        LogLine level(String level)      { this.level = level; return this; }
        LogLine message(String message)  { this.message = message; return this; }

        @Override public String toString() { return time + " [" + level + "] " + message; }
    }

    /*
    A static factory as the only entry point

    - Hide the constructor and expose one static factory named after the domain verb: select(...),
      request(...), validate(...). The caller cannot reach the type any other way, so the DSL controls where
      every chain begins — and, with type-state (Mod003), where it may go next.
    - This is what makes the chain a language instead of a bag of setters: there is a start symbol.
    - The factory also names the required arguments. Below, a DateRange cannot exist without a start date,
      because `from` is the only door and it demands one.
    */
    static void staticFactoryEntry() {
        System.out.println("[Section 2] static factory as the only entry point");

        var range = DateRange.from(LocalDate.of(2026, 1, 1)).to(LocalDate.of(2026, 12, 31));
        System.out.println("  DateRange.from(...).to(...) = " + range);
        System.out.println("  days = " + range.days());

        // new DateRange(...)          // does not compile FROM ANOTHER FILE — the constructor is private
        // new DateRange(null, null)   // an open-ended range cannot be built by accident
        //
        // Careful with this one: private is scoped to the enclosing TOP-LEVEL class (JLS 6.6.1), so those
        // two lines would in fact compile right here, inside Mod001DslBasics. The guarantee holds for every
        // caller outside this file, which is what matters for a published DSL.
        System.out.println("  new DateRange(...) is private — outside this file, `from` is the only way in");
    }

    static final class DateRange {
        private final LocalDate start;
        private final LocalDate end;

        private DateRange(LocalDate start, LocalDate end) { this.start = start; this.end = end; }

        static DateRange from(LocalDate start) { return new DateRange(start, start); }
        DateRange to(LocalDate end)            { return new DateRange(start, end); }

        long days() { return end.toEpochDay() - start.toEpochDay() + 1; }

        @Override public String toString() { return "[" + start + " .. " + end + "]"; }
    }

    /*
    Lambda as receiver — expressing nesting

    - Chaining is flat; most domains are trees (HTML, JSON, menus, forms). To express nesting, a method takes
      a Consumer<Builder>: the DSL creates the child builder, hands it to the lambda, and closes the node
      afterwards. Opening and closing are the DSL's job, so they can never get out of sync.
    - Kotlin would make the child builder the implicit `this` inside the block. Java has no receiver lambdas,
      so the caller writes an explicit parameter name and prefixes each call with it: `ul -> ul.li(...)`.
      That prefix is the visible price of doing this in Java; there is no way around it.
    - Because a body is just a Consumer<Builder>, a fragment is a value: store it, name it, pass it around.
      Mod002 builds a whole HTML e-mail on this one idea.
    */
    static void lambdaAsReceiver() {
        System.out.println("[Section 3] lambda as receiver — nesting");

        String menu = tag("ul", ul -> {
            ul.tag("li", li -> li.text("Home"));
            ul.tag("li", li -> li.tag("b", b -> b.text("Products & more")));
        });
        System.out.println("  " + menu);

        // A body is a value, so a reusable fragment is just a Consumer.
        Consumer<Node> separator = n -> n.tag("li", li -> li.text("---"));
        System.out.println("  " + tag("ul", ul -> { separator.accept(ul); separator.accept(ul); }));
    }

    static String tag(String name, Consumer<Node> body) {
        var node = new Node();
        body.accept(node);
        return "<" + name + ">" + node + "</" + name + ">";
    }

    static final class Node {
        private final StringBuilder out = new StringBuilder();

        void text(String s)                        { out.append(escape(s)); }
        void tag(String name, Consumer<Node> body) { out.append(Mod001DslBasics.tag(name, body)); }

        @Override public String toString() { return out.toString(); }

        private static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    /*
    Typed options as varargs — keeping optional arguments honest

    - Optional settings are the part of a DSL that decays fastest. Two common decays:
      overload explosion (one method per combination), and stringly-typed parameters
      (send(url, "json", "retry")) where nothing catches a typo or a swapped argument.
    - The fix is a small value type per option plus a varargs parameter. Each option is produced by a named
      factory, so the call site reads as prose and the compiler checks every argument.
    - Order-independence comes free, and adding an option is one new factory rather than 2^n new overloads.
    */
    static void typedVarargOptions() {
        System.out.println("[Section 4] typed options as varargs");

        System.out.println("  " + send("https://api.example.com/orders",
                timeout(2_000), retries(3), header("Accept", "application/json")));

        // Order does not matter, and options may be omitted entirely.
        System.out.println("  " + send("https://api.example.com/ping"));

        // send(url, "2000", "3")   // does not compile — a String is not an Option
        System.out.println("  send(url, \"2000\", \"3\")  // rejected: a String is not an Option");
    }

    sealed interface Option permits Timeout, Retries, Header {}
    record Timeout(int millis)              implements Option {}
    record Retries(int count)               implements Option {}
    record Header(String name, String value) implements Option {}

    static Option timeout(int millis)                { return new Timeout(millis); }
    static Option retries(int count)                 { return new Retries(count); }
    static Option header(String name, String value)  { return new Header(name, value); }

    static String send(String url, Option... options) {
        int timeout = 1_000, retries = 0;
        var headers = new ArrayList<String>();
        for (var option : options) {
            switch (option) {                      // sealed + records: the switch is exhaustive
                case Timeout(int millis)       -> timeout = millis;
                case Retries(int count)        -> retries = count;
                case Header(String n, String v) -> headers.add(n + "=" + v);
            }
        }
        return "GET " + url + " (timeout=" + timeout + "ms, retries=" + retries + ", headers=" + headers + ")";
    }

    /*
    When to write a DSL, and what to read

    - Worth it when the same shape is written many times by many people, when invalid combinations are a real
      source of bugs, or when the configuration is read far more often than it is written.
    - Not worth it for a single call site, or when the domain is still moving: a DSL freezes vocabulary, and
      rewriting one costs more than rewriting the twenty calls it replaced.
    - The cheapest DSL is one that feels like something the reader already knows. Exemplars worth studying,
      each next to the module here that imitates it:
        AssertJ              — fluent assertions over a typed subject          (§1 chaining)
        OkHttp Request       — required/optional fields on a builder           (Mod003)
        jOOQ                 — typed SQL; Field<T> carries the column type     (Mod004)
        Hibernate Validator  — declarative constraints, all errors collected   (Mod005)
        Javalin / Spark      — routes registered into a table by a lambda      (§3 receivers)
        parsec / fastparse   — parser combinators from Haskell and Scala       (Mod006)
    - Mockito's when(x.foo()).thenReturn(y) is the odd one out and worth knowing about: it is a *recording*
      DSL. The call inside when(...) really executes against a proxy that logs it; the chain then attaches
      behaviour to that recording. No phantom types involved — just a stateful proxy.
    */
    static void whenToBuildOne() {
        System.out.println("[Section 5] when a DSL pays for itself");
        List.of("many call sites, written by many people",
                "invalid combinations are a real bug source",
                "read far more often than written")
                .forEach(reason -> System.out.println("  + " + reason));
        List.of("a single call site",
                "vocabulary still changing week to week")
                .forEach(reason -> System.out.println("  - " + reason));
    }

    public static void main(String[] args) {
        methodChaining();
        staticFactoryEntry();
        lambdaAsReceiver();
        typedVarargOptions();
        whenToBuildOne();
        System.out.println("Mod001DslBasics finished");
    }
}
