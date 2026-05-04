package pl.training.functionalfeatures;

import java.util.List;
import java.util.Map;

// =================================================================================================
// Section 1: Type pattern (Java 16)
// =================================================================================================

/*
## Type pattern (Java 16)

Pre-Java 16, the "I have an `Object`, what is it?" idiom was three lines:

```java
if (obj instanceof Integer) {
    Integer i = (Integer) obj;     // redundant cast
    use(i);
}
```

Since Java 16, `instanceof` itself can introduce a binding:

```java
if (obj instanceof Integer i) {
    use(i);                        // i is in scope, no cast needed
}
```

- The binding `i` is in scope only in the *positive* branch — wherever the
  compiler can prove that the test succeeded.
- The runtime cost is identical to the manual cast; it is purely a syntax
  improvement.
- The pattern variable behaves like a `final` local: you can read it, you
  cannot assign to it.
*/

// =================================================================================================
// Section 2: Negated pattern flow (flow-sensitive scoping)
// =================================================================================================

/*
## Negated pattern flow (flow-sensitive scoping)

```java
if (!(obj instanceof Integer i)) return;
use(i);                            // i IS in scope here
```

After the `return`, the only way to reach the next statement is for the test
to have succeeded — so the compiler keeps the binding in scope. This is
called *flow-sensitive scoping*.

The same logic applies to `throw`, infinite loops, and any path that the
compiler can prove never falls through.
*/

// =================================================================================================
// Section 3: Combining with &&
// =================================================================================================

/*
## Combining with &&

`obj instanceof String s && !s.isBlank()` works because the right-hand side
of `&&` is only evaluated if the left was true — exactly when the binding `s`
is in scope. The compiler propagates the binding across the `&&`.

This does **not** work with `||`: `obj instanceof String s || s.length() > 0`
is a compile error, because `s` would not be bound on the right path.
*/

// =================================================================================================
// Section 4: Pattern matching with generics
// =================================================================================================

/*
## Pattern matching with generics

A type pattern can use a generic type *only when the compiler can prove the
test is safe given the static type of the operand*:

- `Object o = ...; if (o instanceof List<String> xs) ...` — illegal,
  unchecked cast (Java cannot verify generic type at runtime due to erasure).
- `Map<String, Object> m = ...;
  if (m.get("k") instanceof List<String> xs) ...` — same problem.
- `obj instanceof List<?> xs` — always legal; `?` introduces no unsafe
  assumptions.

For lists of unknown element type, use the wildcard `List<?>` and stream
through with downstream `instanceof` checks.
*/

// =================================================================================================
// Section 5: Replacing the cast-and-check idiom
// =================================================================================================

/*
## Replacing the cast-and-check idiom

Side-by-side comparison: the same business logic written with and without
the type pattern. The new style:

- Removes the redundant cast.
- Eliminates the temporary variable that lived only to hold the cast.
- Makes the boundary "this `Object` is now an `Integer`" explicit at the
  declaration site.

The old style is still legal — but in modern code it is a smell.
*/

public final class Mod007PatternMatchingInstanceof {

    private Mod007PatternMatchingInstanceof() {}

    // --- Section 1: type pattern ---
    static void typePattern() {
        System.out.println("[Section 1] type pattern");

        Object[] payloads = { 42, "hello", 3.14, "world", List.of(1, 2, 3) };
        for (Object p : payloads) {
            if (p instanceof Integer i) {
                System.out.println("  int squared: " + (i * i));
            } else if (p instanceof String s) {
                System.out.println("  string upper: " + s.toUpperCase());
            } else if (p instanceof Double d) {
                System.out.printf( "  double rounded: %d%n", Math.round(d));
            } else {
                System.out.println("  other: " + p);
            }
        }
    }

    // --- Section 2: negated pattern flow ---
    static void negatedPatternFlow() {
        System.out.println("[Section 2] negated pattern flow");

        // The pattern variable is in scope after the early-return because the only
        // way to reach the next line is if the cast succeeded.
        process("alice@example.com");
        process(42);
        process(null);
    }

    private static void process(Object input) {
        if (!(input instanceof String s)) {
            System.out.println("  rejected (not a String): " + input);
            return;
        }
        // s is in scope here — no cast, no extra variable.
        System.out.println("  accepted upper-case: " + s.toUpperCase());
    }

    // --- Section 3: combining with && ---
    static void combiningWithAnd() {
        System.out.println("[Section 3] combining with &&");

        Object[] inputs = { "  ", "alice", 42, "" };
        for (Object obj : inputs) {
            if (obj instanceof String s && !s.isBlank()) {
                // s is in scope on the right-hand side AND inside the body.
                System.out.println("  non-blank string: " + s);
            }
        }
    }

    // --- Section 4: pattern matching with generics (wildcards only) ---
    @SuppressWarnings("unchecked")
    static void patternsWithGenerics() {
        System.out.println("[Section 4] generics in patterns");

        Object payload = List.of("a", "b", "c");

        // Wildcard form is always legal:
        if (payload instanceof List<?> raw) {
            System.out.println("  wildcard list size = " + raw.size());
            // Element check would require an inner pattern per element.
            for (Object e : raw) {
                if (e instanceof String s) {
                    System.out.println("    element: " + s);
                }
            }
        }

        // Inferred parameterised form (Java 21+) is allowed when the operand's static
        // type carries the generic parameter, e.g.
        Map<String, List<String>> m = Map.of("k", List.of("x", "y"));
        if (m.get("k") instanceof List<String> ss) {        // OK — the static type lets the compiler check it
            System.out.println("  parameterised list = " + ss);
        }
    }

    // --- Section 5: pre-Java 16 vs new style ---
    static void replaceCastAndCheck() {
        System.out.println("[Section 5] cast-and-check vs type pattern");

        Object obj = "hello";

        // Old style — three lines per branch, redundant cast.
        String oldStyle;
        if (obj instanceof String) {
            oldStyle = ((String) obj).toUpperCase();
        } else {
            oldStyle = "<not a string>";
        }
        System.out.println("  old style: " + oldStyle);

        // New style — single line per branch, no cast, single binding.
        String newStyle = (obj instanceof String s) ? s.toUpperCase() : "<not a string>";
        System.out.println("  new style: " + newStyle);
    }

    public static void main(String[] args) {
        typePattern();
        negatedPatternFlow();
        combiningWithAnd();
        patternsWithGenerics();
        replaceCastAndCheck();
        System.out.println("Mod007PatternMatchingInstanceof finished");
    }
}
