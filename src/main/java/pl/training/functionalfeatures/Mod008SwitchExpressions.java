package pl.training.functionalfeatures;

import java.time.DayOfWeek;
import java.time.LocalDate;

public final class Mod008SwitchExpressions {

    private Mod008SwitchExpressions() {}

    enum Status { ACTIVE, ARCHIVED, BANNED, GUEST }

    /*
    Arrow form (Java 14)

    switch (day) {
        case MON, TUE, WED, THU, FRI -> "weekday";
        case SAT, SUN                -> "weekend";
    }

    - Right-hand side is a single expression (or a { ... yield ... } block — see §3).
    - No fall-through — every arrow case implicitly breaks. This eliminates the most common bug in classic switches.
    - Multiple labels on one case are written with commas, replacing the awful empty-case fall-through trick of the
      old syntax.
    */
    static void arrowForm() {
        System.out.println("[Section 1] arrow form, multiple labels");

        for (DayOfWeek d : DayOfWeek.values()) {
            String kind = switch (d) {
                case MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY -> "weekday";
                case SATURDAY, SUNDAY                              -> "weekend";
            };
            System.out.println("  " + d + " -> " + kind);
        }
    }

    /*
    switch as an expression

    The whole switch is an expression that produces a value:

    String message = switch (status / 100) {
        case 1 -> "informational";
        case 2 -> "ok";
        case 4 -> "client error";
        case 5 -> "server error";
        default -> "?";
    };

    - The expression form is definitely assigned — every path must produce a value. Falling off the end is a compile
      error.
    - For a switch on a non-enum / non-sealed selector you need a default to make the switch total.
    - This composes well with var: var label = switch (x) { ... };.
    */
    static void switchExpression() {
        System.out.println("[Section 2] switch as expression");

        int[] codes = { 100, 200, 301, 404, 500, 999 };
        for (int code : codes) {
            String family = switch (code / 100) {
                case 1 -> "informational";
                case 2 -> "ok";
                case 3 -> "redirect";
                case 4 -> "client error";
                case 5 -> "server error";
                default -> "unknown";
            };
            System.out.printf("  %3d -> %s%n", code, family);
        }
    }

    /*
    yield for block-bodied cases

    When a case needs a statement (logging, validation, helper computation), use a block and end it with yield:

    case 2 -> {
        log.info("hit {}xx", code / 100);
        yield "ok";
    }

    - yield is the value-returning analogue of return for switch expressions.
    - break does NOT yield — it just terminates the case (statement form only); using it in expression form is a
      compile error.
    */
    static void yieldInBlocks() {
        System.out.println("[Section 3] yield in block-bodied cases");

        for (int code : new int[]{ 204, 418, 503 }) {
            String description = switch (code / 100) {
                case 2 -> {
                    System.out.println("    (logging successful response " + code + ")");
                    yield "ok";
                }
                case 4 -> {
                    System.out.println("    (logging client error "        + code + ")");
                    yield code == 418 ? "i'm a teapot" : "client error";
                }
                case 5 -> "server error";    // single-expression cases stay arrow-only
                default -> "?";
            };
            System.out.printf("  %d -> %s%n", code, description);
        }
    }

    /*
    Exhaustiveness on enums

    When the selector is an enum and every constant is covered by an arrow case, no default is required and the
    compiler enforces total coverage.

    The big payoff is forward compatibility: adding a new constant to the enum turns every existing exhaustive
    switch into a compile error, forcing the author to acknowledge the new case. Compare to a switch with default —
    the default silently absorbs the new constant and you may ship a bug.

    Sealed types (Mod010) get the same compiler-enforced exhaustiveness.
    */
    static void enumExhaustiveness() {
        System.out.println("[Section 4] enum exhaustiveness");

        for (Status s : Status.values()) {
            // No `default` here. If we ever add a new Status constant, this switch
            // becomes a compile error — exactly what we want.
            String policy = switch (s) {
                case ACTIVE   -> "allow all";
                case GUEST    -> "read only";
                case ARCHIVED -> "read only";
                case BANNED   -> "deny";
            };
            System.out.println("  " + s + " -> " + policy);
        }
    }

    /*
    Statement form vs expression form

    Both forms still exist:

    - Statement form — for side effects only (print, throw, mutate). No value is produced.
    - Expression form — produces a value; preferred whenever you would otherwise assign to a temporary in each
      branch.

    The arrow form (§1) can be used in either context. The classic colon-form (case X: ... break;) is still legal
    and still allows fall-through, but in new code there is no reason to choose it over arrows.
    */
    static void statementVsExpression() {
        System.out.println("[Section 5] statement vs expression form");

        var today = LocalDate.now().getDayOfWeek();

        // Statement form — purely for side effects.
        switch (today) {
            case SATURDAY, SUNDAY -> System.out.println("  (weekend) take it easy");
            default               -> System.out.println("  (weekday) get to work");
        }

        // Expression form — produces a value used downstream.
        int hoursOff = switch (today) {
            case SATURDAY, SUNDAY -> 24;
            default               -> 8;
        };
        System.out.println("  hours off today = " + hoursOff);
    }

    public static void main(String[] args) {
        arrowForm();
        switchExpression();
        yieldInBlocks();
        enumExhaustiveness();
        statementVsExpression();
        System.out.println("Mod008SwitchExpressions finished");
    }
}
