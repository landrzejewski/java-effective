package pl.training.functionalprogramming;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

// =================================================================================================
// Section 1: What is a pure function
// =================================================================================================

/*
## What is a pure function

A function is *pure* when it satisfies two properties:

- **Determinism** — same arguments always produce the same result. No
hidden inputs (clock, random, environment, file system).
- **No observable side effects** — calling it does not modify global
state, allocate resources visible to the outside world, mutate its
arguments, throw exceptions for non-error conditions, or print to a
stream.

A pure function is a **mathematical function**: a mapping from its inputs
to its outputs. Everything you need to know about it is in its signature
and its return value.
*/

// =================================================================================================
// Section 2: Referential transparency
// =================================================================================================

/*
## Referential transparency

A piece of code is *referentially transparent* (RT) when it can be
replaced with the value it produces without changing program meaning.

```
let x = pure(args)
use(x, x, x)        ≡   use(pure(args), pure(args), pure(args))
```

Pure functions are referentially transparent by construction. Impure
functions are not — replacing `now()` with the value it returned earlier
gives you a different program.

RT is the property that makes equational reasoning possible: refactoring,
inlining, caching, parallelisation, and unit testing all rely on it.
*/

// =================================================================================================
// Section 3: Side effects in plain code
// =================================================================================================

/*
## Side effects in plain code

Every one of the following breaks RT:

- **Mutation of arguments** — `list.add(...)` on a parameter you did not
  own.
- **Field mutation** — counters, caches, "last value" state.
- **Hidden inputs** — `LocalDate.now()`, `Math.random()`, environment
  variables, file reads.
- **Hidden outputs** — `System.out.println`, logging, network calls.
- **Throwing exceptions for ordinary conditions** — replaces "function
  returns a value" with "function returns a value or unwinds the stack".

None of this is automatically wrong. The point is that *each* breakage
costs reasoning power, so a function that needs only purity should not
incur it.
*/

// =================================================================================================
// Section 4: Immutability
// =================================================================================================

/*
## Immutability

- A class is *immutable* when its observable state never changes after
construction. `record`, `final` fields, defensive copies of mutable
inputs, no setters, no mutable references leaking out.
- An immutable input cannot be mutated by callees, so callers and
implementations can pass references freely without defensive copying.
- "Copy-with-changes" idiom: instead of `obj.setX(v)` you write a
constructor or a `with`-method that returns a new instance.
*/

// =================================================================================================
// Section 5: Why purity pays off
// =================================================================================================

/*
## Why purity pays off

- **Testability** — no test fixtures, no mocking the clock, no dependency
injection setup. The function takes inputs, you assert outputs.
- **Parallelisation** — a pure function has no shared state to race over.
The runtime can split work across cores or hosts safely.
- **Caching / memoisation** — same inputs → same output, so a cache hit
can replace the call entirely.
- **Refactoring** — equational reasoning. A pure expression can be
inlined, factored out, or reordered without changing meaning.
- **Reasoning** — when reading a pure function, you don't have to know
what other code is doing concurrently or in the past.
*/

// =================================================================================================
// Section 6: End-to-end: pure vs impure discount
// =================================================================================================

/*
## End-to-end: pure vs impure discount

Two implementations of the same operation:

- **Impure**: mutates the input list and the orders in place; calling it
twice with the same input applies the discount twice.
- **Pure**: returns a fresh list of fresh `Order` instances; calling it
twice yields the same result; the input list is bit-for-bit unchanged.

The `main` method runs both, and a self-check confirms the pure version
left the input untouched.
*/

public final class Mod001PureFunctionsAndReferentialTransparency {

    private Mod001PureFunctionsAndReferentialTransparency() {}

    // Mutable Order — used only to make the impure example *visibly* mutate.
    static final class MutableOrder {
        String customer;
        double amount;
        MutableOrder(String customer, double amount) { this.customer = customer; this.amount = amount; }
        @Override public String toString() { return "Mutable(" + customer + ", " + amount + ")"; }
    }

    // Immutable Order — same data, no setters.
    record Order(String customer, double amount) {
        Order withAmount(double newAmount) { return new Order(customer, newAmount); }
    }

    // ---- Impure version: mutates input ----
    static void discountImpure(List<MutableOrder> orders, int percent) {
        double factor = 1.0 - percent / 100.0;
        for (var o : orders) o.amount *= factor;     // mutation of caller's data
    }

    // ---- Pure version: returns a new list of new orders ----
    static List<Order> discountPure(List<Order> orders, int percent) {
        double factor = 1.0 - percent / 100.0;
        return orders.stream()
                .map(o -> o.withAmount(o.amount() * factor))
                .toList();
    }

    // --- Section 1+2: purity + RT in one observable example ---
    static void purityAndRt() {
        System.out.println("[Section 1+2] purity + referential transparency");

        // Pure: replacing the call with its value is fine.
        var input = List.of(new Order("alice", 100.0));
        var once  = discountPure(input, 10);
        var twice = discountPure(input, 10);
        System.out.println("  pure once  = " + once);
        System.out.println("  pure twice = " + twice);
        System.out.println("  RT property holds: once.equals(twice) = " + once.equals(twice));
    }

    // --- Section 3: side-effect taxonomy ---
    static void sideEffectsTaxonomy() {
        System.out.println("[Section 3] common side effects (each breaks RT)");

        // Hidden input — depends on call time.
        long t1 = System.currentTimeMillis();
        try { Thread.sleep(2); } catch (InterruptedException ignored) {}
        long t2 = System.currentTimeMillis();
        System.out.println("  System.currentTimeMillis() returned " + t1 + " then " + t2);

        // Hidden output — calling alters the world.
        var counter = new AtomicInteger();
        Runnable impureSink = () -> { counter.incrementAndGet(); System.out.print(""); };
        impureSink.run(); impureSink.run();
        System.out.println("  side-effecting calls advanced counter to " + counter.get());
    }

    // --- Section 4: immutability via record + defensive copy ---
    static void immutabilityPattern() {
        System.out.println("[Section 4] immutability");

        var original = new Order("alice", 100.0);
        var bigger   = original.withAmount(150.0);

        System.out.println("  original = " + original);
        System.out.println("  bigger   = " + bigger);
        System.out.println("  original unchanged after withAmount: "
                + (original.amount() == 100.0));
    }

    // --- Section 6: pure vs impure with a self-check ---
    static void endToEnd() {
        System.out.println("[Section 6] pure vs impure — self-check");

        // Impure path
        var mutables = new ArrayList<>(List.of(
                new MutableOrder("alice", 100.0),
                new MutableOrder("bob",   200.0)));
        var beforeAmounts = mutables.stream().map(o -> o.amount).toList();
        discountImpure(mutables, 10);
        var afterAmounts = mutables.stream().map(o -> o.amount).toList();
        System.out.println("  impure: before=" + beforeAmounts + ", after=" + afterAmounts);
        System.out.println("  impure mutated the input? " + !beforeAmounts.equals(afterAmounts));

        // Pure path
        var immutables = List.of(new Order("alice", 100.0), new Order("bob", 200.0));
        var snapshot   = List.copyOf(immutables);
        var discounted = discountPure(immutables, 10);
        System.out.println("  pure  : input=" + immutables + ", result=" + discounted);
        System.out.println("  pure preserved input? " + immutables.equals(snapshot));

        boolean ok = immutables.equals(snapshot)
                && !beforeAmounts.equals(afterAmounts)
                && discounted.size() == immutables.size();
        System.out.println("  end-to-end self-check: " + (ok ? "✓" : "✗"));
    }

    public static void main(String[] args) {
        purityAndRt();
        sideEffectsTaxonomy();
        immutabilityPattern();
        endToEnd();
        System.out.println("Mod001PureFunctionsAndReferentialTransparency finished");
    }
}
