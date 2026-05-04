package pl.training.functionalfeatures;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// =================================================================================================
// Section 1: Records 30-second recap
// =================================================================================================

/*
## Records 30-second recap

A `record` is an immutable data carrier:

```java
record Point(int x, int y) {}
```

The compiler synthesises:
- a canonical constructor (`Point(int x, int y)`),
- accessor methods (`x()`, `y()`),
- `equals` / `hashCode` based on all components,
- `toString()` like `Point[x=1, y=2]`.

Records are implicitly `final`. They can implement interfaces and add `static`
or `default` methods, but they have no mutable state. The component names are
part of the API — used both by accessors and by record patterns (§2).
*/

// =================================================================================================
// Section 2: Record patterns (Java 21)
// =================================================================================================

/*
## Record patterns (Java 21)

A *record pattern* destructures a record:

```java
case Point(int x, int y) -> ...
```

- The components are matched in canonical order (the order declared in the
  record header).
- Each sub-pattern can itself be a type pattern (`int x`), another record
  pattern (nested), or `var name` for type-inferred bindings.
- The whole record pattern matches if the value `instanceof` the record AND
  every nested sub-pattern matches.
*/

// =================================================================================================
// Section 3: Nested record patterns
// =================================================================================================

/*
## Nested record patterns

```java
case Line(Point(int x1, int y1), Point(int x2, int y2)) -> ...
```

You can deconstruct several levels in a single pattern. Anywhere a sub-pattern
appears, you may use:
- a record pattern (deeper deconstruction),
- a type pattern (`SomeType name`),
- `var name` (infer the type, just bind),
- the unnamed pattern `_` (§6).

Combine with guards (`when`) and the result is a one-line case that does the
work of half a dozen `if`s.
*/

// =================================================================================================
// Section 4: Sealed interfaces + permits
// =================================================================================================

/*
## Sealed interfaces + permits

A `sealed interface` declares a closed set of permitted subtypes:

```java
sealed interface Json permits JsonNull, JsonBool, JsonNumber,
                              JsonString, JsonArray, JsonObject {}
```

Each permitted subtype must declare its kind: `final` (a leaf), `sealed`
(another closed branch), or `non-sealed` (re-opens to anyone). Most ADTs use
records for the leaves — they are implicitly `final`, equality is structural,
and they pair perfectly with record patterns.

The pay-off in pattern matching: a switch over the sealed type with one case
per permitted subtype is automatically exhaustive — no `default` needed, and
adding a new subtype is a compile error in every existing switch.
*/

// =================================================================================================
// Section 5: Algebraic data types in Java
// =================================================================================================

/*
## Algebraic data types in Java

`sealed` + `record` is Java's idiomatic ADT encoding. Pattern matching is the
natural eliminator: each case handles one variant and gets its components
deconstructed in the head.

The same logic in classic Java would use the **visitor pattern** — three
interfaces, several `accept`/`visit` overloads, a new `visit` method on every
visitor every time a variant is added. Pattern matching collapses all of that
into a single switch expression.
*/

// =================================================================================================
// Section 6: Unnamed patterns and variables (Java 22)
// =================================================================================================

/*
## Unnamed patterns and variables (Java 22+)

`_` in a record pattern says "match this slot, but I do not need to bind a
name to it":

```java
case Point(int x, _) -> "x is " + x;
```

Same thing for local variables in `try`/`catch` and lambda parameters when
you accept an argument you do not use:

```java
try { ... } catch (NumberFormatException _) { return -1; }
list.forEach(_ -> counter.increment());
```

Use it whenever a name would only confuse the reader. Since the introduction
of unnamed variables in Java 22, the alternative — picking
half-meaningless names like `ignored` or `unused` — has no advantage left.
*/

// =================================================================================================
// Section 7: Real-world walk — pretty-printer
// =================================================================================================

/*
## Real-world walk — pretty-printer

A single switch expression produces a pretty-printed JSON string by recursing
through the AST. No `default`, no `instanceof` chain, no visitor — just one
case per permitted subtype, each deconstructing its components.
*/

public final class Mod010RecordsSealedAndUnnamedPatterns {

    private Mod010RecordsSealedAndUnnamedPatterns() {}

    // --- Sealed JSON-like AST ---
    sealed interface Json permits JsonNull, JsonBool, JsonNumber, JsonString, JsonArray, JsonObject {}
    record JsonNull()                              implements Json {}
    record JsonBool(boolean value)                 implements Json {}
    record JsonNumber(double value)                implements Json {}
    record JsonString(String value)                implements Json {}
    record JsonArray(List<Json> elements)          implements Json {}
    record JsonObject(Map<String, Json> fields)    implements Json {}

    // A small example tree used by every section's example.
    private static Json sample() {
        Map<String, Json> user = new LinkedHashMap<>();
        user.put("name",   new JsonString("alice"));
        user.put("age",    new JsonNumber(30));
        user.put("active", new JsonBool(true));
        user.put("tags",   new JsonArray(List.of(new JsonString("dev"), new JsonString("admin"))));
        user.put("avatar", new JsonNull());
        return new JsonObject(user);
    }

    // --- Section 1: records recap ---
    static void recordsRecap() {
        System.out.println("[Section 1] records recap");

        var num = new JsonNumber(42);
        System.out.println("  num.value() = " + num.value());
        System.out.println("  toString    = " + num);
        System.out.println("  equality    = " + new JsonNumber(42).equals(num));
    }

    // --- Section 2: record patterns ---
    static void recordPatterns() {
        System.out.println("[Section 2] record patterns");

        Json[] values = {
                new JsonNumber(3.14),
                new JsonString("hello"),
                new JsonBool(true),
                new JsonNull()
        };
        for (Json j : values) {
            String label = switch (j) {
                case JsonNumber(double v) -> "number " + v;
                case JsonString(String s) -> "string '" + s + "'";
                case JsonBool(boolean b)  -> "bool "   + b;
                case JsonNull()           -> "null";
                default                   -> "other";  // never reached; Mod009 §6 demonstrates exhaustiveness
            };
            System.out.println("  " + label);
        }
    }

    // --- Section 3: nested record patterns ---
    static void nestedRecordPatterns() {
        System.out.println("[Section 3] nested record patterns");

        // Build a small "line" sample reusing JsonArray/JsonNumber.
        var pair = new JsonArray(List.of(
                new JsonArray(List.of(new JsonNumber(0), new JsonNumber(0))),
                new JsonArray(List.of(new JsonNumber(3), new JsonNumber(4)))));

        String length = switch (pair) {
            // Outer JsonArray destructures into a List<Json> we then look into.
            case JsonArray(List<Json> xs)
                    when xs.size() == 2
                            && xs.get(0) instanceof JsonArray(List<Json> p1)
                            && p1.size() == 2
                            && p1.get(0) instanceof JsonNumber(double x1)
                            && p1.get(1) instanceof JsonNumber(double y1)
                            && xs.get(1) instanceof JsonArray(List<Json> p2)
                            && p2.size() == 2
                            && p2.get(0) instanceof JsonNumber(double x2)
                            && p2.get(1) instanceof JsonNumber(double y2)
                    -> {
                double dx = x2 - x1, dy = y2 - y1;
                yield String.format("%.2f", Math.hypot(dx, dy));
            }
            default -> "unsupported shape";
        };
        System.out.println("  length = " + length);
    }

    // --- Section 4: sealed + exhaustive switch (no default needed) ---
    static String render(Json j) {
        return switch (j) {
            case JsonNull()                       -> "null";
            case JsonBool(boolean b)              -> Boolean.toString(b);
            case JsonNumber(double v)             -> {
                if (v == Math.floor(v)) yield Long.toString((long) v);
                yield Double.toString(v);
            }
            case JsonString(String s)             -> "\"" + s + "\"";
            case JsonArray(List<Json> es)         -> {
                var sb = new StringBuilder("[");
                for (int i = 0; i < es.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(render(es.get(i)));
                }
                yield sb.append("]").toString();
            }
            case JsonObject(Map<String, Json> fs) -> {
                var sb = new StringBuilder("{");
                boolean first = true;
                for (var e : fs.entrySet()) {
                    if (!first) sb.append(",");
                    sb.append("\"").append(e.getKey()).append("\":").append(render(e.getValue()));
                    first = false;
                }
                yield sb.append("}").toString();
            }
        };
    }

    static void sealedExhaustive() {
        System.out.println("[Section 4] sealed + exhaustive");
        System.out.println("  rendered = " + render(sample()));
    }

    // --- Section 5: ADT — counting nodes by kind, single switch ---
    static void adtNodeCount() {
        System.out.println("[Section 5] ADT — count nodes by kind");

        var counters = new LinkedHashMap<String, Integer>();
        countInto(sample(), counters);
        System.out.println("  " + counters);
    }

    private static void countInto(Json j, Map<String, Integer> counts) {
        // One switch, one case per permitted subtype — the visitor pattern in 8 lines.
        switch (j) {
            case JsonNull()                       -> bump(counts, "null");
            case JsonBool(boolean _)              -> bump(counts, "bool");
            case JsonNumber(double _)             -> bump(counts, "number");
            case JsonString(String _)             -> bump(counts, "string");
            case JsonArray(List<Json> es)         -> { bump(counts, "array");  for (var x : es) countInto(x, counts); }
            case JsonObject(Map<String, Json> fs) -> { bump(counts, "object"); for (var x : fs.values()) countInto(x, counts); }
        }
    }

    private static void bump(Map<String, Integer> counts, String key) {
        counts.merge(key, 1, Integer::sum);
    }

    // --- Section 6: unnamed patterns ---
    static void unnamedPatterns() {
        System.out.println("[Section 6] unnamed patterns");

        // We only care WHETHER a node is a number / string — not its value.
        Json[] mix = { new JsonNumber(1), new JsonString("x"), new JsonBool(true), new JsonNull() };
        for (Json j : mix) {
            String kind = switch (j) {
                case JsonNumber(_) -> "number";
                case JsonString(_) -> "string";
                case JsonBool(_)   -> "bool";
                case JsonNull()    -> "null";
                default            -> "?";
            };
            System.out.println("  " + kind);
        }

        // Unnamed VARIABLE in a lambda: we must accept the parameters but never use them.
        var counter = new int[]{0};
        if (sample() instanceof JsonObject(Map<String, Json> fs)) {
            fs.forEach((_, _) -> counter[0]++);
            System.out.println("  number of top-level fields (lambda with `_, _`) = " + counter[0]);
        }
    }

    // --- Section 7: real-world walk ---
    static void prettyPrinter() {
        System.out.println("[Section 7] pretty-printer");
        System.out.println("  " + render(sample()));
    }

    public static void main(String[] args) {
        recordsRecap();
        recordPatterns();
        nestedRecordPatterns();
        sealedExhaustive();
        adtNodeCount();
        unnamedPatterns();
        prettyPrinter();
        System.out.println("Mod010RecordsSealedAndUnnamedPatterns finished");
    }
}
