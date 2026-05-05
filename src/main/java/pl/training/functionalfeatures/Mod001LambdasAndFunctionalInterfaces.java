package pl.training.functionalfeatures;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

@FunctionalInterface
interface PriceFilter { // domain-specific functional interface
    boolean accept(double price);
}

@FunctionalInterface
interface TriFunction<A, B, C, R> {
    R apply(A a, B b, C c);

    // composition is easy to add — same shape as Function.andThen
    default <V> TriFunction<A, B, C, V> andThen(Function<? super R, ? extends V> after) {
        return (a, b, c) -> after.apply(apply(a, b, c));
    }
}

public final class Mod001LambdasAndFunctionalInterfaces {

    private Mod001LambdasAndFunctionalInterfaces() {}

    record Product(String name, double price) {}

    private static final List<Product> PRODUCTS = List.of(
            new Product("Mouse",   29.99),
            new Product("Keyboard", 89.50),
            new Product("Monitor", 320.00),
            new Product("Cable",     5.50));

    /*
    Functional interfaces (SAM)

    - A functional interface is an interface with exactly one abstract method (plus any number of default and static
      methods). The single abstract method is called the SAM — Single Abstract Method.
    - The SAM concept matters because Java's lambda syntax is desugared into an implementation of some functional
      interface chosen by context.
    - The @FunctionalInterface annotation is optional documentation. It also turns "this interface accidentally got a
      second abstract method" into a compile-time error — preventing a future change from silently breaking every
      caller that passed a lambda.
    - Common ready-made SAM types live in java.util.function: Predicate<T>, Function<T,R>, Consumer<T>, Supplier<T>,
      UnaryOperator<T> and friends (covered in Mod002).
    */
    static void functionalInterfaceSAM() {
        System.out.println("[Section 1] @FunctionalInterface");

        PriceFilter expensive = price -> price >= 50.0;
        PRODUCTS.stream()
                .filter(p -> expensive.accept(p.price()))
                .forEach(p -> System.out.println("  expensive: " + p));
    }

    /*
    Lambda expression syntax

    The body of a lambda is sugar for the SAM implementation:

    () -> 42                            // no parameters
    x  -> x * 2                         // one parameter, no parentheses
    (x, y) -> x + y                     // two parameters
    (int x, int y) -> x + y             // explicit types (rarely needed)
    (x, y) -> { int r = x + y; return r; }  // block body, explicit return

    - The compiler picks the target type (Predicate<T>, Function<T,R>, …) by looking at the surrounding context:
      assignment, method argument, or cast.
    - Single-statement bodies omit return. Block bodies require it (or fall off the end for void).
    - A lambda used in an Object context (e.g., assigning to a var) is illegal unless cast:
      var f = (Function<Integer, Integer>) x -> x + 1;. Plain var f = x -> x + 1; does not compile because there is
      no target type.
    */
    static void lambdaSyntax() {
        System.out.println("[Section 2] lambda syntax");

        Comparator<Product> byPrice  = (a, b) -> Double.compare(a.price(), b.price());
        Comparator<Product> byName   = (a, b) -> a.name().compareTo(b.name());
        Comparator<Product> blockBody = (a, b) -> {
            int byPriceCmp = Double.compare(a.price(), b.price());
            return byPriceCmp != 0 ? byPriceCmp : a.name().compareTo(b.name());
        };

        var sorted = PRODUCTS.stream().sorted(byPrice).toList();
        System.out.println("  cheapest first: " + sorted);
        System.out.println("  by name [0]:    " + PRODUCTS.stream().sorted(byName).findFirst().orElseThrow());
        System.out.println("  block-body [0]: " + PRODUCTS.stream().sorted(blockBody).findFirst().orElseThrow());
    }

    /*
    Lambda vs anonymous class

    Both compile to "an implementation of a functional interface", but they differ in three observable ways:

    - this semantics. Inside an anonymous class, this refers to the anonymous class instance. Inside a lambda, this
      refers to the enclosing class — there is no anonymous instance to point at.
    - Field shadowing. An anonymous class can declare its own fields and methods; those names shadow the enclosing
      class's. A lambda has no body for its own fields — the enclosing scope's names always win.
    - Class file count. Each anonymous class produces a new .class file and allocates a new instance every time you
      reach the expression. A lambda is compiled to a single hidden method that is bound at runtime via
      invokedynamic — typically a single shared instance for stateless lambdas.

    Net: prefer lambdas; reach for anonymous classes only when you need fields, multiple methods, or a non-this
    reference.
    */
    private final String label = "outer";

    void lambdaVsAnonymousThis() {
        System.out.println("[Section 3] lambda vs anonymous class");

        // Lambda — `this` is the enclosing instance.
        Runnable lambda = () -> System.out.println("  lambda this.label = " + this.label);

        // Anonymous class — `this` is the anonymous instance, which has no `label`.
        Runnable anon = new Runnable() {
            // own state shadows nothing in the enclosing class — separate instance
            @Override public void run() {
                System.out.println("  anon   this.getClass() = " + this.getClass().getSimpleName());
            }
        };

        lambda.run();
        anon.run();
    }

    /*
    Variable capture and "effectively final"

    - A lambda can read a local variable from the enclosing method only if the variable is effectively final —
      declared final or simply never reassigned after its initialization.
    - Instance and static fields are not subject to this rule because they live on the heap; the lambda just
      dereferences them at call time.
    - The restriction exists because the lambda may outlive the stack frame that created it (it can be stored,
      returned, or run on another thread). A captured local is copied into the lambda's invocation site by the
      compiler; allowing reassignment would split a single name into two values.
    */
    static void effectivelyFinalCapture() {
        System.out.println("[Section 4] effectively final");

        int multiplier = 3; // never reassigned → effectively final
        Function<Integer, Integer> times = x -> x * multiplier;
        System.out.println("  7 * " + multiplier + " = " + times.apply(7));

        // The line below would not compile because `multiplier` would no longer be
        // effectively final:
        //
        //     multiplier = 4;
        //     // error: local variables referenced from a lambda must be final or effectively final
    }

    /*
    A custom functional interface

    java.util.function covers most cases, but a few common shapes are missing: three-argument functions, throwing
    functions, primitive-pair predicates. When you need one, declare it. Naming convention: noun describing what it
    produces (e.g., TriFunction, Validator).
    */
    static void customFunctionalInterface() {
        System.out.println("[Section 5] custom TriFunction");

        TriFunction<String, Integer, Boolean, String> describe =
                (label, count, exclaim) -> count + " " + label + (exclaim ? "!" : ".");

        var withSuffix = describe.andThen(s -> ">> " + s);
        System.out.println("  " + describe.apply("apples", 3, true));
        System.out.println("  " + withSuffix.apply("oranges", 2, false));
    }

    /*
    When NOT to use a lambda

    - The body is more than ~3 lines or contains nested control flow. Extract it into a private named method and
      pass this::name instead. The stack trace, the IDE call hierarchy, and the diff become readable.
    - The behaviour is reused in more than one place — a name lets you reuse it.
    - The lambda's intent is unclear from the surrounding context. A self-explanatory method name beats a clever
      one-liner.
    - The function has multiple return paths or throws checked exceptions — block bodies in lambdas need explicit
      return/throw and turn into noise quickly.
    */
    static void whenToExtract() {
        System.out.println("[Section 6] extract when complex");

        // BAD — imagine 8 lines of business rules in one lambda. Hard to test, hard to debug.
        var pricedBad = PRODUCTS.stream()
                .filter(p -> {
                    if (p.name().isBlank()) return false;
                    if (p.price() <= 0) return false;
                    if (p.price() > 1000) return false;
                    return p.name().length() <= 30;
                })
                .toList();

        // GOOD — a named method conveys intent and shows up in stack traces.
        var pricedGood = PRODUCTS.stream()
                .filter(Mod001LambdasAndFunctionalInterfaces::isReasonableProduct)
                .toList();

        System.out.println("  bad-style filter kept " + pricedBad.size() + " products");
        System.out.println("  good-style filter kept " + pricedGood.size() + " products");
    }

    private static boolean isReasonableProduct(Product p) {
        return !p.name().isBlank() && p.price() > 0 && p.price() <= 1000 && p.name().length() <= 30;
    }

    public static void main(String[] args) {
        functionalInterfaceSAM();
        lambdaSyntax();
        new Mod001LambdasAndFunctionalInterfaces().lambdaVsAnonymousThis();
        effectivelyFinalCapture();
        customFunctionalInterface();
        whenToExtract();
        System.out.println("Mod001LambdasAndFunctionalInterfaces finished");
    }
}
