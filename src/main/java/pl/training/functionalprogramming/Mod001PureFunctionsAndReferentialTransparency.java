package pl.training.functionalprogramming;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/*
Purity — the one idea the rest of this package is built on

Every other module here (folds, Option, Validation, State, IO) exists to let you keep writing pure
functions in situations where the obvious Java solution would not be pure. So it is worth being precise
about what "pure" means before spending nine modules defending it.

A pure function is one you can understand completely by reading its signature and its body. Nothing it
does depends on when you call it, how many times you call it, or what any other thread is doing. That
sounds like a restriction. In practice it buys five things:

- Testability - no fixtures, no mocked clock, no dependency injection. You pass inputs, you assert on
  the returned value. There is nothing else to set up because there is nothing else involved.
- Parallelisation - a pure function has no shared state, so there is nothing to race over. The runtime
  can split the work across cores without you auditing anything.
- Caching / memoisation - same inputs always give the same output, so a cache hit can replace the call
  outright. You cannot safely memoise a function that reads the clock.
- Refactoring - you can inline a pure call, extract it into a variable, or reorder two independent ones
  without changing what the program means. This is "equational reasoning", and it is what makes large
  refactors mechanical rather than scary.
- Local reasoning - when reading a pure function you never have to ask "but what else touched this
  object first?" The answer is always: nothing.

The cost is that real programs must read files, print output and record time. Purity does not forbid
that. It says: push those effects to the edges, keep the middle pure, and make the boundary visible.
Mod010 shows the extreme version of that discipline.
*/

public final class Mod001PureFunctionsAndReferentialTransparency {

    private Mod001PureFunctionsAndReferentialTransparency() {}

    // Mutable Order - exists only so the impure example can *visibly* damage its caller's data.
    static final class MutableOrder {
        String customer;
        double amount;
        MutableOrder(String customer, double amount) { this.customer = customer; this.amount = amount; }
        @Override public String toString() { return "Mutable(" + customer + ", " + amount + ")"; }
    }

    // Immutable Order - the same data, but no way to change it after construction.
    // withAmount is the "copy with one field changed" idiom that replaces a setter.
    record Order(String customer, double amount) {
        Order withAmount(double newAmount) { return new Order(customer, newAmount); }
    }

    // ---- Impure version: reaches into the caller's objects and rewrites them ----
    static void discountImpure(List<MutableOrder> orders, int percent) {
        double factor = 1.0 - percent / 100.0;
        for (var o : orders) o.amount *= factor;     // mutation of data the caller still owns
    }

    // ---- Pure version: builds a new list of new orders and returns it ----
    static List<Order> discountPure(List<Order> orders, int percent) {
        double factor = 1.0 - percent / 100.0;
        return orders.stream()
                .map(o -> o.withAmount(o.amount() * factor))
                .toList();
    }

    /*
    Section 1 - what "pure" and "referentially transparent" actually mean

    A function is pure when it satisfies two properties:

    - Determinism - the same arguments always produce the same result. No hidden inputs: no clock, no
      random source, no environment variable, no file system, no database.
    - No observable side effects - calling it does not modify global state, does not mutate its
      arguments, does not print, does not log, and does not throw for ordinary (non-exceptional)
      outcomes.

    Put differently: a pure function is a mathematical function, a plain mapping from inputs to outputs.
    Everything worth knowing about it is in its signature and its return value.

    Referential transparency (RT)

    A piece of code is referentially transparent when you can replace it with the value it produced and
    the program still means the same thing. Written out:

        var x = pure(args);
        use(x, x, x);          is equivalent to        use(pure(args), pure(args), pure(args));

    Both directions matter. You can pull a repeated call out into a variable (fewer computations), or
    push a variable back into its uses (fewer names) - and RT promises neither change alters behaviour.

    Pure functions are referentially transparent by construction. Impure ones are not, and the
    counterexample is one line: replace LocalDate.now() with the date it returned a moment ago and you
    have written a different program.

    RT is the property, not purity itself, that does the useful work. Refactoring, inlining, caching,
    parallelisation and unit testing are all just RT being cashed in.
    */
    static void purityAndRt() {
        System.out.println("[Section 1] purity + referential transparency");

        // Calling discountPure twice with the same input gives two equal results, so either call can be
        // replaced by its value. That is RT, demonstrated rather than asserted.
        var input = List.of(new Order("alice", 100.0));
        var once  = discountPure(input, 10);
        var twice = discountPure(input, 10);
        System.out.println("  pure once  = " + once);
        System.out.println("  pure twice = " + twice);
        System.out.println("  RT property holds: once.equals(twice) = " + once.equals(twice));
    }

    /*
    Section 2 - the five ways ordinary Java code breaks referential transparency

    - Mutation of arguments - calling list.add(...) on a parameter you did not create. The caller now
      holds different data than it passed in, and it never asked for that.
    - Field mutation - counters, caches, "last value seen" state. The second call behaves differently
      from the first.
    - Hidden inputs - LocalDate.now(), Math.random(), System.getenv(), reading a file. The result
      depends on something that is not in the argument list.
    - Hidden outputs - System.out.println, logging, sending a network request. Calling the function
      changes the world, so calling it twice is not the same as calling it once.
    - Throwing for ordinary conditions - this swaps "the function returns a value" for "the function
      returns a value OR unwinds the stack", which is a second, invisible return channel. Mod005 shows
      how to put that outcome back into the return type where the caller can see it.

    None of these is automatically a bug. Programs that never do any of them are programs that do
    nothing observable. The point is that each one costs you a specific reasoning power from the list in
    the file header, so a function that does not need to pay should not.
    */
    static void sideEffectsTaxonomy() {
        System.out.println("[Section 2] common side effects (each breaks RT)");

        // Hidden input: the result depends on *when* you call it, not on any argument.
        long t1 = System.currentTimeMillis();
        try { Thread.sleep(2); } catch (InterruptedException ignored) {}
        long t2 = System.currentTimeMillis();
        System.out.println("  System.currentTimeMillis() returned " + t1 + " then " + t2
                + " -> two calls, two answers, same (empty) argument list");

        // Hidden output: each call leaves a trace, so call count is observable from outside.
        var counter = new AtomicInteger();
        Runnable impureSink = () -> { counter.incrementAndGet(); System.out.print(""); };
        impureSink.run(); impureSink.run();
        System.out.println("  side-effecting calls advanced counter to " + counter.get()
                + " -> you cannot replace two calls with one");
    }

    /*
    Section 3 - immutability, the enabling technique

    Purity is about functions; immutability is about the data they pass around. You need both, because a
    "pure" function that hands out a reference to a mutable object has only moved the problem.

    - A class is immutable when its observable state never changes after construction: a record, or
      final fields with no setters, defensive copies of any mutable constructor argument, and no mutable
      reference leaking out through a getter.
    - Because an immutable input cannot be modified by whoever receives it, callers and implementations
      can share references freely. No defensive copying, no "who owns this list" conventions, no
      surprise when a collaborator keeps a reference.
    - Replace obj.setX(v) with the copy-with-changes idiom: a method that returns a *new* instance with
      one field different. That is Order.withAmount below.

    The obvious objection is cost: does this not allocate constantly? For small records, yes, and it
    does not matter. For large structures the answer is structural sharing, which is Mod004.
    */
    static void immutabilityPattern() {
        System.out.println("[Section 3] immutability");

        var original = new Order("alice", 100.0);
        var bigger   = original.withAmount(150.0);

        System.out.println("  original = " + original);
        System.out.println("  bigger   = " + bigger);
        System.out.println("  original unchanged after withAmount: "
                + (original.amount() == 100.0));
    }

    /*
    Section 4 - the same operation written both ways, with a self-check

    Two implementations of "apply a percentage discount to these orders":

    - discountImpure mutates the caller's list contents in place. It returns void, so its signature
      tells you nothing about what it did. Calling it twice applies the discount twice - the operation
      is not idempotent and not replaceable by its result, because it has no result.
    - discountPure returns a fresh list of fresh Order instances. Calling it twice gives two equal
      lists, and the input is untouched afterwards.

    The self-check below confirms exactly that: the impure call changed its input, the pure call did
    not. Watch which one you could safely retry after a failure, cache, or run on two threads at once.
    */
    static void endToEnd() {
        System.out.println("[Section 4] pure vs impure - self-check");

        // Impure path: capture the amounts before and after, and compare.
        var mutables = new ArrayList<>(List.of(
                new MutableOrder("alice", 100.0),
                new MutableOrder("bob",   200.0)));
        var beforeAmounts = mutables.stream().map(o -> o.amount).toList();
        discountImpure(mutables, 10);
        var afterAmounts = mutables.stream().map(o -> o.amount).toList();
        System.out.println("  impure: before=" + beforeAmounts + ", after=" + afterAmounts);
        System.out.println("  impure mutated the input? " + !beforeAmounts.equals(afterAmounts));

        // Pure path: snapshot the input, run the function, confirm the input is identical afterwards.
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
