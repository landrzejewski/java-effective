package pl.training.functionalfeatures;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Mod012PatternMatchingInstanceof {

    private Mod012PatternMatchingInstanceof() {}

    /*
    Type pattern (Java 16)

    Pre-Java 16, the "I have an Object, what is it?" idiom was three lines:

    if (obj instanceof Integer) {
        Integer i = (Integer) obj;     // redundant cast
        use(i);
    }

    Since Java 16, instanceof itself can introduce a binding:

    if (obj instanceof Integer i) {
        use(i);                        // i is in scope, no cast needed
    }

    - The binding i is in scope only in the positive branch - wherever the compiler can prove that the test
      succeeded.
    - The runtime cost is identical to the manual cast; it is purely a syntax improvement.
    - The pattern variable behaves like a final local: you can read it, you cannot assign to it.
    */
    static void typePattern() {
        System.out.println("[Section 1] type pattern");

        Object[] payloads = { 42, "hello", 3.14, "world", List.of(1, 2, 3) };
        for (Object p : payloads) {
            if (p instanceof Integer i) {
                System.out.println("  int squared: " + (i * i));
            } else if (p instanceof String s) {
                System.out.println("  string upper: " + s.toUpperCase());
            } else if (p instanceof Double d) {
                System.out.printf(Locale.ROOT, "  double rounded: %d%n", Math.round(d));
            } else {
                System.out.println("  other: " + p);
            }
        }
    }

    /*
    Negated pattern flow (flow-sensitive scoping)

    if (!(obj instanceof Integer i)) return;
    use(i);                            // i IS in scope here

    After the return, the only way to reach the next statement is for the test to have succeeded - so the compiler
    keeps the binding in scope. This is called flow-sensitive scoping.

    The same logic applies to throw, infinite loops, and any path that the compiler can prove never falls through.
    */
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
        // s is in scope here - no cast, no extra variable.
        System.out.println("  accepted upper-case: " + s.toUpperCase());
    }

    /*
    Combining with &&

    obj instanceof String s && !s.isBlank() works because the right-hand side of && is only evaluated if the left
    was true - exactly when the binding s is in scope. The compiler propagates the binding across the &&.

    This does not work with ||: obj instanceof String s || s.length() > 0 is a compile error, because s would not
    be bound on the right path.
    */
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

    /*
    Generic types in a pattern

    A type pattern is checked at runtime, and generic type arguments are erased. So the rule is: the compiler
    accepts a parameterised type pattern only when it can prove the test from the STATIC type of the operand
    alone, without needing runtime information it does not have.

    The static type of the operand is what decides, and that is easy to miss because the two lines look alike:

        Object o;                       if (o instanceof List<String> xs)          // ERROR
        List<String> l;                 if (l instanceof ArrayList<String> xs)     // OK
        Map<String, Object> m;          if (m.get("k") instanceof List<String> xs) // ERROR: get returns Object
        Map<String, List<String>> m;    if (m.get("k") instanceof List<String> xs) // OK: get returns List<String>

    In the ERROR cases javac reports it as an illegal generic type for instanceof. It is not being pedantic:
    the check it would have to emit cannot exist, and letting it through would produce a ClassCastException
    later, far from the cast.

    When the element type genuinely is unknown, match the wildcard List<?> and test the elements individually.
    */
    static void patternsWithGenerics() {
        System.out.println("[Section 4] generics in patterns");

        // The operand is Object, so only the wildcard form compiles.
        Object payload = List.of("a", "b", 42);
        // if (payload instanceof List<String> xs)   // does not compile: illegal generic type for instanceof
        if (payload instanceof List<?> raw) {
            System.out.println("  List<?> matched, size = " + raw.size());
            // The element type is recovered one element at a time, which IS checkable at runtime.
            var strings = raw.stream().filter(e -> e instanceof String).map(String.class::cast).toList();
            System.out.println("  string elements       = " + strings);
        }

        // Here the static type carries the argument, so the parameterised pattern is provable and legal.
        Map<String, List<String>> typed = Map.of("k", List.of("x", "y"));
        if (typed.get("k") instanceof List<String> ss) {   // get() returns List<String>, not Object
            System.out.println("  provable from the static type -> " + ss);
        }

        // Change the map's value type to Object and the very same line stops compiling.
        Map<String, Object> untyped = Map.of("k", List.of("x", "y"));
        // if (untyped.get("k") instanceof List<String> ss)  // does not compile: get() returns Object
        if (untyped.get("k") instanceof List<?> ss) {
            System.out.println("  same line, Object values -> wildcard only, size = " + ss.size());
        }
    }

    /*
    Replacing the cast-and-check idiom

    Side-by-side comparison: the same business logic written with and without the type pattern. The new style:

    - Removes the redundant cast.
    - Eliminates the temporary variable that lived only to hold the cast.
    - Makes the boundary "this Object is now an Integer" explicit at the declaration site.

    The old style is still legal - but in modern code it is a smell.
    */
    static void replaceCastAndCheck() {
        System.out.println("[Section 5] cast-and-check vs type pattern");

        Object obj = "hello";

        // Old style - three lines per branch, redundant cast.
        String oldStyle;
        if (obj instanceof String) {
            oldStyle = ((String) obj).toUpperCase();
        } else {
            oldStyle = "<not a string>";
        }
        System.out.println("  old style: " + oldStyle);

        // New style - single line per branch, no cast, single binding.
        String newStyle = (obj instanceof String s) ? s.toUpperCase() : "<not a string>";
        System.out.println("  new style: " + newStyle);
    }

    public static void main(String[] args) {
        typePattern();
        negatedPatternFlow();
        combiningWithAnd();
        patternsWithGenerics();
        replaceCastAndCheck();
        System.out.println("Mod012PatternMatchingInstanceof finished");
    }
}
