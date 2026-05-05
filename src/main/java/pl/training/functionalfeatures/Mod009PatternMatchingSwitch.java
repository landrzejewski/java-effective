package pl.training.functionalfeatures;

import java.util.List;

public final class Mod009PatternMatchingSwitch {

    private Mod009PatternMatchingSwitch() {}

    // tiny sealed type for §6
    sealed interface Event permits Login, Logout, Heartbeat {}
    record Login(String user) implements Event {}
    record Logout(String user) implements Event {}
    record Heartbeat(int seq) implements Event {}

    /*
    Type patterns in switch (Java 21)

    A switch over a value of static type Object (or any other supertype) can dispatch on the runtime type, with a
    binding scoped to the case body:

    String label = switch (obj) {
        case Integer i -> "int: " + i;
        case String s  -> "string: " + s;
        case null      -> "null";
        default        -> "other";
    };

    - The type label Integer i is a type pattern — it both tests obj instanceof Integer and binds i for the body.
    - Inside a single case, the binding is scoped exactly to that arrow's body.
    - A type pattern is exhaustive only when the selector's static type is a sealed type (Mod010); otherwise a
      default is required.
    */
    static void typePatternsInSwitch() {
        System.out.println("[Section 1] type patterns in switch");

        Object[] inputs = { 42, "hello", 3.14, List.of(1, 2), 7L };
        for (Object o : inputs) {
            String description = switch (o) {
                case Integer i -> "int " + i;
                case Long l    -> "long " + l;
                case Double d  -> String.format("double %.1f", d);
                case String s  -> "string '" + s + "'";
                default        -> "other (" + o.getClass().getSimpleName() + ")";
            };
            System.out.println("  " + description);
        }
    }

    /*
    Guarded patterns with when

    A guard is an extra boolean test attached to a pattern:

    case Integer i when i > 0 -> "positive";
    case Integer i            -> "non-positive";

    - The guard fires after the type test and the binding succeed.
    - Two cases of the same type but different guards must appear in the order most-specific-first; a later case is
      reached only if the earlier guard fails.
    - The compiler does not consider guards when computing exhaustiveness — a switch where every case is guarded
      must still have a fallback.
    */
    static void guardedWithWhen() {
        System.out.println("[Section 2] guards with `when`");

        Object[] inputs = { -3, 0, 7, "hi", "" };
        for (Object o : inputs) {
            String classification = switch (o) {
                case Integer i when i > 0   -> "positive int " + i;
                case Integer i when i < 0   -> "negative int " + i;
                case Integer i              -> "zero";              // matches Integer & not the guards above
                case String s when s.isEmpty() -> "empty string";
                case String s               -> "string of length " + s.length();
                default                     -> "other";
            };
            System.out.println("  " + o + " -> " + classification);
        }
    }

    /*
    Null pattern

    By default, a switch selector that is null throws NullPointerException (same as before pattern matching).
    Pattern-matching switches let you explicitly handle null:

    switch (obj) {
        case null      -> "missing";
        case String s  -> "string: " + s;
        default        -> "other";
    }

    - case null must be a separate label.
    - The combined label case null, default -> handles "null or anything not matched above" in one shot — useful
      for "tolerate everything weird" fallbacks.
    */
    static void nullPattern() {
        System.out.println("[Section 3] null pattern");

        Object[] inputs = { "alice", null, 42 };
        for (Object o : inputs) {
            String description = switch (o) {
                case null     -> "<null>";
                case String s -> "string: " + s;
                default       -> "other (" + o.getClass().getSimpleName() + ")";
            };
            System.out.println("  " + description);
        }
    }

    /*
    Combined null + default

    case null, default -> ... is a single label that fires for null or for any value not matched by an earlier
    case. It is the cleanest way to write a total switch that also tolerates null.

    The order matters: the combined label must be last, just like a plain default. Earlier case null -> plus a
    separate default -> is also legal and sometimes clearer when each path does different work.
    */
    static void combinedNullDefault() {
        System.out.println("[Section 4] case null, default ->");

        Object[] inputs = { "alice", null, 42, 3.14 };
        for (Object o : inputs) {
            // Single fallback label catches both null and unhandled types.
            String description = switch (o) {
                case String s         -> "string: " + s;
                case null, default    -> "tolerated: " + o;
            };
            System.out.println("  " + description);
        }
    }

    /*
    Dominance rules

    A switch over a polymorphic selector must avoid unreachable cases. If a later case's pattern is a subtype of an
    earlier case's pattern (or otherwise fully covered by it), the compiler rejects the later case as unreachable:

    switch (n) {
        case Number num -> "number";
        case Integer i  -> "int";        // ERROR: dominated by Number
    }

    The fix is to put the more specific pattern first, or to add a guard to the broader one. Guards influence
    reachability — a guarded Number case does not dominate a later Integer case because the guard might fail.
    */
    static void dominanceRules() {
        System.out.println("[Section 5] dominance rules");

        Object[] inputs = { 1, 2L, 3.0, "x" };
        for (Object o : inputs) {
            // Integer must come BEFORE Number, otherwise the compiler rejects
            // `case Integer i ->` as unreachable.
            String description = switch (o) {
                case Integer i  -> "Integer " + i;        // most specific first
                case Number n   -> "Number "  + n + " (" + n.getClass().getSimpleName() + ")";
                default         -> "non-number";
            };
            System.out.println("  " + description);
        }
    }

    /*
    Exhaustiveness with sealed types

    When the selector is a sealed interface, the compiler knows the closed universe of subtypes and enforces total
    coverage at compile time. Adding a new permitted subtype later breaks every existing switch — the same payoff as
    exhaustive enum switches.

    Mod010 has the full record-pattern + sealed-types story; this section just shows the trivial sealed switch to
    set up the next module.
    */
    static void sealedExhaustiveness() {
        System.out.println("[Section 6] sealed-type exhaustiveness");

        Event[] events = { new Login("alice"), new Heartbeat(7), new Logout("alice") };
        for (Event e : events) {
            // No `default` — adding a new permitted subtype later turns this into a compile error.
            String description = switch (e) {
                case Login(String user)  -> "login: "    + user;
                case Logout(String user) -> "logout: "   + user;
                case Heartbeat(int seq)  -> "heartbeat #" + seq;
            };
            System.out.println("  " + description);
        }
    }

    public static void main(String[] args) {
        typePatternsInSwitch();
        guardedWithWhen();
        nullPattern();
        combinedNullDefault();
        dominanceRules();
        sealedExhaustiveness();
        System.out.println("Mod009PatternMatchingSwitch finished");
    }
}
