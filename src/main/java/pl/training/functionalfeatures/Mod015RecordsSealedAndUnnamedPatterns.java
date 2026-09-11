package pl.training.functionalfeatures;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Mod015RecordsSealedAndUnnamedPatterns {

    private Mod015RecordsSealedAndUnnamedPatterns() {}

    // --- A sealed JSON-like AST: the running example for the whole module ---
    sealed interface Json permits JsonNull, JsonBool, JsonNumber, JsonString, JsonArray, JsonObject {}

    record JsonNull()                  implements Json {}
    record JsonBool(boolean value)     implements Json {}
    record JsonString(String value)    implements Json {
        JsonString {                                        // compact constructor: reject, do not repair
            if (value == null) {
                throw new IllegalArgumentException("JsonString value must not be null");
            }
        }
    }
    record JsonNumber(double value)    implements Json {
        JsonNumber {                                        // JSON has no NaN or Infinity
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new IllegalArgumentException("not representable in JSON: " + value);
            }
        }
    }
    record JsonArray(List<Json> elements) implements Json {
        JsonArray {                                         // defensive copy: records are only SHALLOWLY immutable
            elements = List.copyOf(elements);
        }
    }
    record JsonObject(Map<String, Json> fields) implements Json {
        JsonObject {
            fields = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }
    }

    // --- Geometry, used to show real nested deconstruction ---
    sealed interface Shape permits Circle, Rectangle, Segment {}
    record Point(double x, double y) {}
    record Circle(Point center, double radius)         implements Shape {}
    record Rectangle(Point topLeft, Point bottomRight) implements Shape {}
    record Segment(Point from, Point to)               implements Shape {}

    private static Json sample() {
        Map<String, Json> user = new LinkedHashMap<>();
        user.put("name",   new JsonString("alice"));
        user.put("age",    new JsonNumber(30));
        user.put("active", new JsonBool(true));
        user.put("tags",   new JsonArray(List.of(new JsonString("dev"), new JsonString("admin"))));
        user.put("avatar", new JsonNull());
        return new JsonObject(user);
    }

    /*
    Records, and the constructor you actually write

    A record declares its state in the header and the compiler synthesises the canonical constructor, the
    accessors, equals, hashCode and toString from it. Records are implicitly final, so a record is always a
    leaf in a type hierarchy.

    The compact constructor is the piece that turns a record from a struct into a proper value type:

        record JsonNumber(double value) {
            JsonNumber {                                    // no parameter list, no assignment
                if (Double.isNaN(value)) throw new IllegalArgumentException(...);
            }
        }

    - It runs BEFORE the fields are assigned. Reassigning the parameter (value = ...) normalises the input;
      the compiler writes the field assignment for you afterwards.
    - Its two jobs are validating (reject impossible states) and normalising (trim a string, copy a
      collection). Do both here and every instance in the program is valid by construction.
    - The defensive copy is not optional. A record holding a List is only as immutable as that List: without
      List.copyOf the caller keeps a reference to the same mutable list and can change your "immutable" value
      after the fact.

    Records can implement interfaces, add static factories and extra methods, and be declared locally inside a
    method (Mod009). What they cannot do is add mutable state or extend a class.
    */
    static void recordsAndValidation() {
        System.out.println("[Section 1] records, compact constructors, real immutability");

        var num = new JsonNumber(42);
        System.out.println("  accessor  = " + num.value());
        System.out.println("  toString  = " + num);
        System.out.println("  equals    = " + new JsonNumber(42).equals(num) + "  (structural, component by component)");

        // Validation: an impossible value never reaches a field.
        try {
            new JsonNumber(Double.NaN);
        } catch (IllegalArgumentException e) {
            System.out.println("  rejected  = " + e.getMessage());
        }

        // Defensive copy: the caller's later mutation cannot reach inside the record.
        var mutableSource = new java.util.ArrayList<Json>(List.of(new JsonString("first")));
        var array = new JsonArray(mutableSource);
        mutableSource.add(new JsonString("sneaked in"));
        System.out.println("  caller mutated its list afterwards, record still has "
                + array.elements().size() + " element(s)");
    }

    /*
    Record patterns

    A record pattern deconstructs a record in the position where a type pattern would go:

        case JsonNumber(double v) -> ...

    - Components are matched in canonical order, by position, not by name.
    - Each slot takes a sub-pattern: a type pattern (double v), var to infer the type (var v), a nested record
      pattern, or the unnamed pattern _ (Section 6).
    - The pattern matches when the value is an instance of the record AND every sub-pattern matches.
    - A record pattern works in instanceof as well as in switch.

    The gain over an accessor call is that the test and the extraction become one expression, so there is no
    window in which you have the object but have not yet checked it.
    */
    static void recordPatterns() {
        System.out.println("[Section 2] record patterns");

        Json[] values = { new JsonNumber(3.14), new JsonString("hello"), new JsonBool(true), new JsonNull() };
        for (Json j : values) {
            String label = switch (j) {
                case JsonNumber(double v) -> "number " + v;
                case JsonString(String s) -> "string '" + s + "'";
                case JsonBool(boolean b)  -> "bool "   + b;
                case JsonNull()           -> "null";
                // JsonArray and JsonObject are the two permitted subtypes this switch does not name,
                // so a default is required. Section 4 covers every case and drops it.
                default                   -> "container";
            };
            System.out.println("  " + label);
        }

        // The same pattern in instanceof, with var instead of a spelled-out type.
        Json j = new JsonString("inline");
        if (j instanceof JsonString(var text)) {
            System.out.println("  instanceof + record pattern -> " + text.toUpperCase());
        }
    }

    /*
    Nested record patterns

    A sub-pattern can itself be a record pattern, to any depth. That is where the syntax stops being a
    convenience and starts removing real code:

        case Segment(Point(var x1, var y1), Point(var x2, var y2)) -> ...

    One line performs six operations: two type tests on the Points, and four component extractions, all named
    and all in scope for the body. Written with accessors it is a type test, two locals, four getter calls, and
    nothing to stop you from reading a component of the wrong Point.

    Nesting composes with guards: add `when` after the pattern and the whole shape plus a condition on the
    extracted values becomes a single case label.
    */
    static void nestedRecordPatterns() {
        System.out.println("[Section 3] nested record patterns");

        Shape[] shapes = {
                new Segment(new Point(0, 0), new Point(3, 4)),
                new Circle(new Point(1, 1), 2.5),
                new Rectangle(new Point(0, 10), new Point(10, 0)),
                new Circle(new Point(0, 0), 0)
        };

        for (Shape shape : shapes) {
            String description = switch (shape) {
                // Two levels of deconstruction and a guard, in one label.
                case Circle(Point(var cx, var cy), double r) when r == 0 ->
                        String.format(Locale.ROOT, "degenerate circle at (%.0f,%.0f)", cx, cy);
                case Circle(Point(var cx, var cy), double r) ->
                        String.format(Locale.ROOT, "circle at (%.0f,%.0f) area %.2f", cx, cy, Math.PI * r * r);
                case Segment(Point(var x1, var y1), Point(var x2, var y2)) ->
                        String.format(Locale.ROOT, "segment length %.2f", Math.hypot(x2 - x1, y2 - y1));
                case Rectangle(Point(var l, var t), Point(var r, var b)) ->
                        String.format(Locale.ROOT, "rectangle %.0fx%.0f", Math.abs(r - l), Math.abs(t - b));
            };
            System.out.println("  " + description);
        }

        // For contrast, the same segment length without record patterns.
        Shape shape = shapes[0];
        if (shape instanceof Segment) {
            Segment segment = (Segment) shape;
            Point from = segment.from();
            Point to = segment.to();
            double length = Math.hypot(to.x() - from.x(), to.y() - from.y());
            System.out.printf(Locale.ROOT, "  without patterns: 6 lines for the same %.2f%n", length);
        }
    }

    /*
    Sealed types and exhaustiveness

    A sealed interface names its permitted implementations:

        sealed interface Json permits JsonNull, JsonBool, JsonNumber, JsonString, JsonArray, JsonObject {}

    Every permitted subtype must state how it continues the hierarchy: final (a leaf), sealed (another closed
    branch), or non-sealed (deliberately reopened). Records are implicitly final, which is why sealed
    interface plus records is the standard encoding.

    The payoff is a compiler-checked closed world. A switch that covers every permitted subtype needs no
    default, and the day someone adds a seventh subtype, every such switch in the codebase stops compiling and
    points at the code that has to be updated. A default would have swallowed the new case silently.

    A switch with no default over a sealed type is compiled with a guard that throws MatchException if the
    hierarchy changed after compilation without recompiling this class, so the closed world is enforced at
    runtime too.
    */
    static String render(Json json) {
        return switch (json) {                              // no default: the compiler checks the coverage
            case JsonNull()               -> "null";
            case JsonBool(boolean b)      -> Boolean.toString(b);
            case JsonNumber(double v)     -> v == Math.floor(v) ? Long.toString((long) v) : Double.toString(v);
            case JsonString(String s)     -> "\"" + s + "\"";
            case JsonArray(List<Json> es) -> es.stream().map(Mod015RecordsSealedAndUnnamedPatterns::render)
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
            case JsonObject(Map<String, Json> fs) -> fs.entrySet().stream()
                    .map(e -> "\"" + e.getKey() + "\":" + render(e.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        };
    }

    static void sealedExhaustive() {
        System.out.println("[Section 4] sealed types: exhaustive without a default");
        System.out.println("  compact render = " + render(sample()));
    }

    /*
    Algebraic data types

    sealed plus record is Java's encoding of an algebraic data type: a closed sum of product types. Pattern
    matching is its eliminator, and the two were designed together.

    The classic alternative is the visitor pattern: a visitor interface with one visit method per variant, an
    accept method on every variant, and a new method on every existing visitor each time a variant is added.
    Adding a variant there is a mechanical change across many files, and nothing forces you to handle it in a
    visitor you forgot about.

    With sealed plus switch, the variants live in one file, each operation is one switch, and the compiler is
    the thing that finds every place a new variant has to be handled.
    */
    static void adtNodeCount() {
        System.out.println("[Section 5] one switch replaces a visitor");

        var counters = new LinkedHashMap<String, Integer>();
        countInto(sample(), counters);
        System.out.println("  nodes by kind = " + counters);
    }

    private static void countInto(Json json, Map<String, Integer> counts) {
        switch (json) {
            case JsonNull()                       -> bump(counts, "null");
            case JsonBool(boolean _)              -> bump(counts, "bool");
            case JsonNumber(double _)             -> bump(counts, "number");
            case JsonString(String _)             -> bump(counts, "string");
            case JsonArray(List<Json> es)         -> { bump(counts, "array");  es.forEach(x -> countInto(x, counts)); }
            case JsonObject(Map<String, Json> fs) -> { bump(counts, "object"); fs.values().forEach(x -> countInto(x, counts)); }
        }
    }

    private static void bump(Map<String, Integer> counts, String key) {
        counts.merge(key, 1, Integer::sum);
    }

    /*
    Unnamed patterns and variables (Java 22)

    The underscore says "something goes here, and I will not refer to it". It is allowed in four places:

        case JsonNumber(_)                      // a record component you do not need
        case JsonArray(List<Json> _)            // the same, with the type spelled out
        catch (NumberFormatException _)         // an exception you only need to have caught
        map.forEach((_, value) -> ...)          // a lambda parameter the body ignores
        for (var _ : items) count++;            // a loop variable used only to count

    Why it is more than cosmetic: a name is a promise that something is worth naming. `ignored`, `unused` and
    `tmp` all break that promise while still occupying the reader's attention, and an unused named variable is
    indistinguishable at a glance from one you forgot to use. The underscore is checkable by the compiler: you
    cannot read it, so it cannot silently become live again.

    It also removes the last reason to write a full record pattern when you only care about the shape.
    */
    static void unnamedPatterns() {
        System.out.println("[Section 6] unnamed patterns and variables");

        Json[] mix = { new JsonNumber(1), new JsonString("x"), new JsonBool(true), new JsonNull() };
        for (Json j : mix) {
            String kind = switch (j) {
                case JsonNumber(_)            -> "number";     // the value is irrelevant, only the shape
                case JsonString(_)            -> "string";
                case JsonBool(_)              -> "bool";
                case JsonNull()               -> "null";
                case JsonArray(List<Json> _)  -> "array";
                case JsonObject(Map<String, Json> _) -> "object";
            };
            System.out.print("  " + kind);
        }
        System.out.println();

        // Unnamed variable in a catch: we need the branch, not the exception object.
        System.out.println("  parse(\"12\")  = " + parseOrDefault("12"));
        System.out.println("  parse(\"abc\") = " + parseOrDefault("abc") + "  (catch (NumberFormatException _))");

        // Unnamed lambda parameters: forEach requires two, the body wants neither.
        if (sample() instanceof JsonObject(Map<String, Json> fields)) {
            var visited = new java.util.concurrent.atomic.AtomicInteger();
            fields.forEach((_, _) -> visited.incrementAndGet());
            System.out.println("  fields visited via (_, _) = " + visited.get());
        }
    }

    private static int parseOrDefault(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException _) {
            return -1;
        }
    }

    /*
    Putting it together: an indenting pretty-printer

    Section 4 rendered the tree to a single compact line. The same traversal with an extra depth parameter
    produces formatted output, and the shape of the code does not change at all: one case per variant, each
    deconstructing what it needs, recursing on its children.

    That stability is the argument for the whole style. Adding an operation over the AST means writing one new
    switch, not touching six classes. Adding a variant to the AST means the compiler lists every switch that
    now needs a case.
    */
    static void prettyPrinter() {
        System.out.println("[Section 7] the same traversal, formatted output");
        System.out.println(pretty(sample(), 1));
    }

    private static String pretty(Json json, int depth) {
        String pad = "  ".repeat(depth);
        String closingPad = "  ".repeat(depth - 1);
        return switch (json) {
            case JsonNull(), JsonBool(_), JsonNumber(_), JsonString(_) -> render(json);
            case JsonArray(List<Json> es) when es.isEmpty() -> "[]";
            case JsonArray(List<Json> es) -> es.stream()
                    .map(e -> pad + pretty(e, depth + 1))
                    .collect(java.util.stream.Collectors.joining(",\n", "[\n", "\n" + closingPad + "]"));
            case JsonObject(Map<String, Json> fs) -> fs.entrySet().stream()
                    .map(e -> pad + "\"" + e.getKey() + "\": " + pretty(e.getValue(), depth + 1))
                    .collect(java.util.stream.Collectors.joining(",\n", "{\n", "\n" + closingPad + "}"));
        };
    }

    public static void main(String[] args) {
        recordsAndValidation();
        recordPatterns();
        nestedRecordPatterns();
        sealedExhaustive();
        adtNodeCount();
        unnamedPatterns();
        prettyPrinter();
        System.out.println("Mod015RecordsSealedAndUnnamedPatterns finished");
    }
}
