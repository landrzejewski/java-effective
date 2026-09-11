package pl.training.functionalprogramming;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import pl.training.functionalprogramming.Mod004ImmutableDataStructures.Branch;
import pl.training.functionalprogramming.Mod004ImmutableDataStructures.Leaf;
import pl.training.functionalprogramming.Mod004ImmutableDataStructures.Tree;

/*
The State monad - keeping mutable-looking code pure

This is the module most Java developers find alien, so it is worth saying plainly what problem it solves
before any of the machinery appears.

THE PROBLEM. Plenty of useful computations need to carry something along as they run: a random number
generator's seed, a counter for generating ids, a stack, a cache, an accumulating log. The Java answer is
a mutable field, and it works - but it takes away everything Mod001 argued for. A method that reads and
writes a field is not deterministic, cannot be tested without setting up that field, cannot be safely
run on two threads, and cannot be understood without knowing what ran before it.

THE OBVIOUS FIX, AND WHY IT IS NOT ENOUGH. Make the state an explicit parameter and return the updated
copy alongside the result:

    Pair<Integer, Long> nextInt(long seed)     // returns (the value, the new seed)

That is pure. It is also miserable to use, because the caller now has to hand-thread the seed through
every single step, and one mistyped variable name silently produces a wrong answer that still compiles.
Section 1 shows exactly how bad this gets.

THE STATE MONAD. Wrap that function shape in a type:

    record State<S, A>(Function<S, Pair<A, S>> run)

Read the type out loud: "a State<S, A> is a recipe that, GIVEN a starting state of type S, produces a
value of type A and a new state of type S". Three things follow, and they are the whole idea:

1. A State is a description, not a result. Building one runs nothing. You get an answer only when you
   call .run().apply(initialState) - exactly like the lazy thunks of Mod007, and exactly like the IO
   values of Mod010.

2. flatMap does the threading for you. You write "get a value, then use it to decide the next step" and
   the seed is passed from step to step automatically, correctly, invisibly. That plumbing is the only
   thing the monad actually does.

3. The result is still completely pure. Same starting state in, same everything out - always. Section 7
   runs a hundred dice rolls twice from the same seed and checks the two runs are identical.

HOW THE STATE FLOWS. In a chain like a.flatMap(x -> b).flatMap(y -> c), the state is threaded like this:

    s0 ──> [ a ] ──> s1 ──> [ b ] ──> s2 ──> [ c ] ──> s3
             │               │               │
             └─> x           └─> y           └─> final value

Each box receives the state its predecessor produced. You never write s0, s1, s2 - they exist only
inside flatMap. What you write is the values x and y and how they connect.

WHY "MONAD". The word names a shape, not a mystery. A type is a monad when it has two operations that
obey three laws:

    pure(a)          wrap a plain value with no effect
    flatMap(f)       chain a step that itself returns another wrapped value

You already use monads constantly: Optional.flatMap chains steps that might be absent, Stream.flatMap
chains steps that produce many results, CompletableFuture.thenCompose chains steps that finish later.
State.flatMap chains steps that carry a state along. The pattern is identical every time; only the
"effect" being threaded changes. That is the entire payoff of learning the word - once you see flatMap,
you know how the type composes without reading its documentation.
*/

public final class Mod008StateMonad {

    private Mod008StateMonad() {}

    // =================================================================================================
    // Pair + State
    // =================================================================================================

    public record Pair<A, B>(A first, B second) {}

    /*
    State<S, A> - a function from a starting state to (a value, an ending state).

    It really is just that one function, wrapped in a record so it can carry methods. Everything below is
    convenience built on top; nothing is hidden.
    */
    public record State<S, A>(Function<S, Pair<A, S>> run) {

        /*
        map - change the produced value, leave the state flow alone.

        Line by line: given a starting state s, run this computation to get (value, newState), apply f to
        the value, and return the transformed value with the SAME new state. f knows nothing about the
        state and cannot affect it - which is exactly the guarantee map gives you.
        */
        public <B> State<S, B> map(Function<A, B> f) {
            return new State<>(s -> {
                var p = run.apply(s);                                 // (a, s1)
                return new Pair<>(f.apply(p.first()), p.second());    // (f(a), s1)
            });
        }

        /*
        flatMap - the operation the whole module exists for. Chain a second computation that DEPENDS on
        the first one's value, threading the state through automatically.

        The body is three steps, and it is worth reading each sub-expression separately because this is
        the one piece of code that makes State click:

            var p = run.apply(s);          1. Run THIS computation on the incoming state.
                                              p is (a, s1): the value produced, and the state afterwards.

            f.apply(p.first())             2. Hand that value to f. f returns a *new State* - still a
                                              recipe, still not run. This is where the dependency lives:
                                              which computation happens next was decided by the value a.

            .run.apply(p.second())         3. Run that new recipe on s1 - the state the FIRST step left
                                              behind, not the original s. This single line is the entire
                                              "plumbing" the monad does for you.

        Compare with Optional.flatMap, whose body is "if present, call f, else stay empty". Same shape:
        unwrap, call f, and let the type handle the bookkeeping the caller would otherwise write by hand.
        */
        public <B> State<S, B> flatMap(Function<A, State<S, B>> f) {
            return new State<>(s -> {
                var p = run.apply(s);                        // 1. run this step: (a, s1)
                return f.apply(p.first())                    // 2. choose the next step using a
                        .run.apply(p.second());              // 3. run it on s1, not on s
            });
        }

        // -------------------------------------------------------------------------------------------
        // The four standard primitives.
        //
        // Everything above manipulates the VALUE. These four are how you touch the STATE itself, and
        // they are the vocabulary you will meet in any State implementation in any language. Without
        // them State looks like a random-number trick; with them it is a general tool.
        // -------------------------------------------------------------------------------------------

        /** pure(a): produce a, change nothing. The monad's "do nothing" - like Optional.of. */
        public static <S, A> State<S, A> pure(A value) {
            return new State<>(s -> new Pair<>(value, s));
        }

        /** get(): read the current state and hand it back as the value. State unchanged. */
        public static <S> State<S, S> get() {
            return new State<>(s -> new Pair<>(s, s));
        }

        /** set(newState): replace the state, ignoring whatever was there.
         *  The textbook signature is State<S, Unit>, because the produced value is meaningless. We
         *  return the new state instead - strictly more useful, and it avoids introducing a Unit type. */
        public static <S> State<S, S> set(S newState) {
            return new State<>(_ -> new Pair<>(newState, newState));
        }

        /** modify(f): apply f to the current state. This is the read-modify-write of the pure world -
         *  the thing you would have written as `this.field = f(this.field)`. */
        public static <S> State<S, S> modify(UnaryOperator<S> f) {
            return new State<>(s -> { S next = f.apply(s); return new Pair<>(next, next); });
        }

        /** gets(f): read a PROJECTION of the state rather than all of it. get().map(f), named. */
        public static <S, A> State<S, A> gets(Function<S, A> f) {
            return new State<>(s -> new Pair<>(f.apply(s), s));
        }

        // -------------------------------------------------------------------------------------------
        // sequence / traverse - "do this stateful thing N times"
        // -------------------------------------------------------------------------------------------

        /*
        sequence turns a List of recipes into one recipe producing a List:

            List<State<S, A>>  ->  State<S, List<A>>

        The textbook definition is a foldRight of flatMaps:

            sequence([])       = pure([])
            sequence(x :: xs)  = x.flatMap(h -> sequence(xs).map(t -> h :: t))

        which is elegant and, in Java, a stack-overflow waiting to happen: each flatMap nests a real
        frame, so a list of 100000 recipes builds a 100000-deep call chain when run. That is the same
        missing-tail-calls problem as Mod003, and real libraries answer it with the trampoline from
        Mod003 Section 5.

        We take the simpler way out and implement it as a loop that threads the state explicitly. Note
        what that means: this method does by hand, ONCE, in one place, exactly the bookkeeping that
        Section 1 shows being done by hand at every call site. That is the deal the abstraction offers.
        */
        public static <S, A> State<S, List<A>> sequence(List<State<S, A>> states) {
            return new State<>(initial -> {
                var out = new ArrayList<A>(states.size());
                S current = initial;
                for (var st : states) {
                    var p = st.run().apply(current);
                    out.add(p.first());
                    current = p.second();      // the next step starts where this one ended
                }
                return new Pair<>(List.copyOf(out), current);
            });
        }

        /** traverse: build a recipe for each item, then sequence them. map-then-sequence, in one step. */
        public static <S, A, B> State<S, List<B>> traverse(List<A> items, Function<A, State<S, B>> f) {
            return sequence(items.stream().map(f).toList());
        }
    }

    // =================================================================================================
    // Example 1: a pure random number generator
    //
    // This is the classic State example because randomness is the purest form of "carries hidden state".
    // java.util.Random keeps its seed in a mutable field, which is precisely why you cannot reason about
    // it: nextInt() returns something different every call, so it is not a function at all.
    //
    // The algorithm below is a linear congruential generator (LCG) - the same one java.util.Random uses,
    // with its exact constants. The state is a 48-bit seed; each step multiplies, adds, and masks off
    // the high bits, then takes the top 32 bits of the result as the output. (48-bit state, 32-bit
    // output - the mask is what makes it 48 rather than 64.)
    //
    // It is a fine teaching generator and a poor cryptographic one. Do not use an LCG for anything
    // security-related.
    // =================================================================================================

    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long INCREMENT  = 0xBL;
    private static final long MASK       = (1L << 48) - 1;    // keep the low 48 bits: this is the state

    /** One step of the generator: advance the 48-bit seed, and produce a non-negative int from it. */
    static State<Long, Integer> nextInt() {
        return new State<>(seed -> {
            long next = (seed * MULTIPLIER + INCREMENT) & MASK;   // the new state
            int  out  = (int) (next >>> 16);                      // top 32 bits of the 48-bit seed
            // Fold negatives into the positive range. Not `Math.abs`, which is broken here:
            // abs(Integer.MIN_VALUE) is still negative. -(x+1) has no such edge case.
            if (out < 0) out = -(out + 1);
            return new Pair<>(out, next);
        });
    }

    /**
     * A number in [lo, hi], inclusive. Built with map, not flatMap - the range does not depend on the
     * random value, it only reshapes it, and map is the operation for "transform the value, leave the
     * state alone".
     *
     * Caveat worth knowing: taking a modulus introduces a slight bias toward the low end of the range
     * whenever the span does not divide evenly into the generator's output range. Irrelevant for dice in
     * a teaching example, not acceptable for a shuffle in a card game. The fix is rejection sampling -
     * redraw when the value falls in the biased tail - which needs flatMap, because whether to redraw
     * depends on the value you got.
     */
    static State<Long, Integer> between(int loInclusive, int hiInclusive) {
        int span = hiInclusive - loInclusive + 1;
        return nextInt().map(n -> loInclusive + Math.floorMod(n, span));
    }

    /** A double in [0, 1). Same shape again: derive from nextInt with map.
     *  Note the divisor: 2^31, not Integer.MAX_VALUE. Dividing by MAX_VALUE would return exactly 1.0
     *  whenever the masked value IS MAX_VALUE - which is reachable, since nextInt() maps
     *  Integer.MIN_VALUE onto Integer.MAX_VALUE - and the range would be [0, 1] instead of [0, 1). */
    static State<Long, Double> nextDouble() {
        return nextInt().map(n -> (n & 0x7fffffff) / 2147483648.0);
    }

    // =================================================================================================
    // Example 2: numbering the leaves of a tree
    //
    // Included because random numbers make State look like a special-purpose trick. This one has nothing
    // to do with randomness: the state is just a counter, and the job is "walk this immutable tree and
    // give every leaf the next number".
    //
    // In imperative Java you would use a field or an int[1] holding a counter and mutate it during the
    // walk. Here the counter is threaded by flatMap, the traversal stays a pure function, and the result
    // is deterministic and testable.
    // =================================================================================================

    /** Produce the current counter value, then advance it. The classic "generate a fresh id". */
    static State<Integer, Integer> freshLabel() {
        return State.<Integer>get()                              // read the counter
                .flatMap(n -> State.<Integer>set(n + 1)          // store counter+1
                        .map(_ -> n));                           // but produce the OLD value
    }

    /**
     * Label every leaf. Notice that this reads like an ordinary recursive tree walk - the counter is
     * nowhere in the signature of the traversal logic, it is threaded by flatMap. Compare against the
     * version with a mutable counter field: same shape, but this one is a pure function.
     */
    static State<Integer, Tree<String>> labelLeaves(Tree<String> tree) {
        return switch (tree) {
            case Leaf<String>(String v) ->
                    freshLabel().map(n -> new Leaf<>(v + "#" + n));
            case Branch<String>(Tree<String> l, Tree<String> r) ->
                    labelLeaves(l).flatMap(labelledLeft ->        // label the left subtree...
                    labelLeaves(r).map(labelledRight ->           // ...then the right, continuing the count
                            new Branch<>(labelledLeft, labelledRight)));
        };
    }

    static String renderTree(Tree<String> t) {
        return switch (t) {
            case Leaf<String>(String v)                          -> v;
            case Branch<String>(Tree<String> l, Tree<String> r)  -> "(" + renderTree(l) + " " + renderTree(r) + ")";
        };
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    /*
    Section 1 - threading state by hand, with no State type at all

    Before introducing the abstraction, feel the problem it removes. Below is a pure random generator
    written as two plain static methods - no State, no monad, nothing clever:

        long stepSeed(long seed)     advance the seed
        int  valueOf(long seed)      derive a value from a seed

    Both are pure. Both are trivially testable. And using them is genuinely unpleasant, because every
    call site has to keep the seed moving by hand:

        long s0 = 42;
        long s1 = stepSeed(s0);   int a = valueOf(s1);
        long s2 = stepSeed(s1);   int b = valueOf(s2);
        long s3 = stepSeed(s2);   int c = valueOf(s3);

    Three observations, and they are the entire motivation for the rest of the module:

    - Half the code is bookkeeping. Of six statements, three exist only to move the seed along. The two
      lines that express the actual intent - "give me three numbers" - are buried.
    - The bookkeeping is error-prone in the worst possible way. Write stepSeed(s1) on the third line
      instead of stepSeed(s2) and you get a duplicated value. It compiles, it runs, it returns a
      plausible number, and no test that only checks the type will notice.
    - It does not compose. You cannot extract "roll three dice" into a helper without that helper taking
      a seed and returning a seed, so the pollution spreads to every method in the call chain.

    Section 2 keeps the purity and deletes the bookkeeping.
    */
    static long stepSeed(long seed) { return (seed * MULTIPLIER + INCREMENT) & MASK; }
    static int  valueOf(long seed)  { int v = (int) (seed >>> 16); return v < 0 ? -(v + 1) : v; }

    static void statePassingPattern() {
        System.out.println("[Section 1] threading state by hand (no State type yet)");

        long s0 = 42L;
        long s1 = stepSeed(s0);  int a = valueOf(s1);
        long s2 = stepSeed(s1);  int b = valueOf(s2);
        long s3 = stepSeed(s2);  int c = valueOf(s3);

        System.out.println("  three values: " + a + ", " + b + ", " + c);
        System.out.println("  final seed:   " + s3);
        System.out.println("  6 statements, 3 of which are pure bookkeeping.");
        System.out.println("  Mistype stepSeed(s1) as stepSeed(s0) on line 2 and it still compiles,");
        System.out.println("  still runs, and silently returns the wrong sequence.");
    }

    /*
    Section 2 - State<S, A>

        record State<S, A>(Function<S, Pair<A, S>> run) {
            <B> State<S, B> map(Function<A, B> f);
            <B> State<S, B> flatMap(Function<A, State<S, B>> f);
        }

    A State<S, A> is a *recipe*: give it a starting state and it yields a value plus a new state. The
    two things to internalise:

    - Nothing runs until you call .run().apply(initialState). Building a State, mapping it, flatMapping
      it - all of that assembles a description and performs no work. This is the same "describe now, run
      later" split as the thunks in Mod007 and the IO values in Mod010.
    - map transforms the value; flatMap chains a step that depends on the value AND threads the state.
      The full line-by-line walkthrough of flatMap is in the comment on the method itself, and it is the
      one piece of code in this module worth reading twice.

    Below, the same computation is run twice from the same seed. Identical results, because a State is a
    pure function of its input - it has no memory and nothing to reset between runs.
    */
    static void stateMonadIntro() {
        System.out.println("[Section 2] State<S, A>");

        State<Long, String> labelled = nextInt().map(n -> "rolled " + n);
        System.out.println("  built the recipe - nothing has run yet");
        System.out.println("  labelled.run(42) = " + labelled.run().apply(42L));
        System.out.println("  labelled.run(42) = " + labelled.run().apply(42L) + "   <- same input, same output");
        System.out.println("  labelled.run(99) = " + labelled.run().apply(99L) + "   <- different seed, different output");
    }

    /*
    Section 3 - get, set, modify, gets: touching the state itself

    map and flatMap operate on the value. These four operate on the state, and they are the standard
    vocabulary you will find in every State implementation in every language:

        get()        produce the current state as the value              (read)
        set(x)       replace the state                                    (write)
        modify(f)    apply f to the state                                 (read-modify-write)
        gets(f)      produce f(state) as the value, state unchanged       (read a projection)

    The correspondence with imperative code is exact:

        int n = this.counter;          becomes    get()
        this.counter = 5;              becomes    set(5)
        this.counter++;                becomes    modify(n -> n + 1)
        int len = this.buffer.size();  becomes    gets(List::size)

    Same operations, same order, same meaning. The difference is that these are values you can pass
    around and compose, and that the "field" being touched is threaded explicitly rather than shared.

    freshLabel() combines two of them into the classic id generator: read the counter, store counter+1,
    but produce the OLD value. Written imperatively it is `return counter++;` - and the fact that both
    versions take a moment to read correctly is not a coincidence, it is what post-increment does.
    */
    static void statePrimitives() {
        System.out.println("[Section 3] get / set / modify / gets");

        System.out.println("  get().run(7)             = " + State.<Integer>get().run().apply(7)
                + "   (value = state, state unchanged)");
        System.out.println("  set(99).run(7)           = " + State.<Integer>set(99).run().apply(7)
                + "  (state replaced)");
        System.out.println("  modify(n -> n*2).run(7)  = " + State.<Integer>modify(n -> n * 2).run().apply(7));
        System.out.println("  gets(n -> \"n=\"+n).run(7)  = "
                + State.<Integer, String>gets(n -> "n=" + n).run().apply(7));

        // freshLabel: `return counter++` written as a pure value.
        var threeIds = State.sequence(List.of(freshLabel(), freshLabel(), freshLabel()));
        System.out.println("  three freshLabel() calls from 0 = " + threeIds.run().apply(0));
        System.out.println("  ^ values [0,1,2] and the counter ended at 3");
    }

    /*
    Section 4 - randomness as State<Long, Integer>

    A random generator is the archetypal stateful computation: each call must produce something different
    from the last, which is only possible if something is carried between calls. java.util.Random carries
    it in a mutable field. We carry it in the state parameter, and get purity back for free.

    - nextInt()        one step of the LCG; produces a non-negative int.
    - between(lo, hi)  reshapes that into a range, using map (the range does not depend on the value).
    - nextDouble()     the same, producing a double in [0, 1).

    The property that matters, and which java.util.Random cannot offer: the same seed always produces the
    same sequence, so a test that depends on random values is fully reproducible without mocking
    anything. The demo below runs the same die twice from seed 42 and gets the same number - not because
    of a lucky collision, but because it is the same pure function applied to the same argument.
    */
    static void randomViaState() {
        System.out.println("[Section 4] random as State<Long, Integer>");

        var die = between(1, 6);
        var p1 = die.run().apply(42L);
        var p2 = die.run().apply(p1.second());     // continue from where the first roll left off
        System.out.println("  from seed 42: " + p1.first() + ", then " + p2.first());
        System.out.println("  same seed twice -> " + die.run().apply(42L).first()
                + " and " + die.run().apply(42L).first() + " (identical, by construction)");
        System.out.printf(java.util.Locale.ROOT, "  nextDouble from seed 42 = %.6f%n",
                nextDouble().run().apply(42L).first());
    }

    /*
    Section 5 - composing stateful computations

    Three dice rolls, written as a chain of flatMaps:

        State<Long, List<Integer>> threeRolls =
                die.flatMap(a ->
                die.flatMap(b ->
                die.map   (c -> List.of(a, b, c))));

    Read it as three bindings: "let a be a roll, then let b be a roll, then let c be a roll, then produce
    [a, b, c]". The seed never appears. It is threaded by flatMap, exactly as walked through in the
    comment on the method - each step runs on the state its predecessor produced.

    Note the last step is map, not flatMap. The rule is simple: use flatMap when the function returns
    another State, and map when it returns a plain value. Here the final step produces a List, not a
    recipe, so map is correct - and using flatMap would not compile.

    THE COST, STATED HONESTLY. That staircase of nested lambdas is ugly, and it gets worse with each
    step. Languages with do-notation or for-comprehensions write the same thing flat:

        for {                          // Scala
          a <- die
          b <- die
          c <- die
        } yield List(a, b, c)

    That is pure syntax over exactly these flatMap calls - no extra power, just no staircase. Java has no
    such syntax, which is the honest reason the State monad stays rare in Java even where it fits. When
    the steps are uniform, sequence and traverse (Section 6) avoid the nesting entirely; the staircase is
    only forced when each step genuinely differs.
    */
    static void composingStateful() {
        System.out.println("[Section 5] composing stateful computations");

        var die = between(1, 6);
        State<Long, List<Integer>> threeRolls =
                die.flatMap(a ->
                die.flatMap(b ->
                die.map   (c -> List.of(a, b, c))));

        var result = threeRolls.run().apply(42L);
        System.out.println("  threeRolls(42) = " + result.first());
        System.out.println("  final seed     = " + result.second());
        System.out.println("  the seed appears nowhere in the three-line chain - flatMap threaded it");
    }

    /*
    Section 6 - sequence and traverse, and a non-random example

    Two helpers for when you have many stateful computations instead of a few:

    - sequence(states)    List<State<S, A>>  ->  State<S, List<A>>
                          Run them left to right, threading the state, collecting the values.
    - traverse(items, f)  For each item build a State via f, then sequence the lot.
                          This is map-then-sequence, and it is the one you actually reach for.

    They matter because they flatten the staircase from Section 5: rolling 100 dice with flatMap would be
    100 levels of nesting, while sequence handles any number with no nesting at all.

    The second demo is the one to pay attention to if randomness has made State look like a niche trick.
    Numbering the leaves of an immutable tree has nothing to do with random numbers: the state is a plain
    counter, and the job is a bog-standard recursive walk. Look at labelLeaves - it reads like any tree
    traversal you have written, except the counter is threaded by flatMap instead of living in a mutable
    field, so the whole traversal stays a pure, deterministic, trivially testable function.

    That is the general shape. Anything you would implement with "a field that the walk keeps updating" -
    id generation, symbol tables, parser positions, undo logs, a running cache - is a State computation.
    */
    static void sequenceAndTraverse() {
        System.out.println("[Section 6] sequence / traverse, and a non-random example");

        var die = between(1, 6);

        // Five rolls with no nesting.
        var fiveRolls = State.sequence(List.of(die, die, die, die, die));
        System.out.println("  sequence of 5 dice (seed 42)  = " + fiveRolls.run().apply(42L).first());

        // traverse: the range depends on the item, so each item needs its own recipe.
        var trav = State.traverse(List.of(1, 2, 3), position -> between(1, position * 10));
        System.out.println("  traverse([1,2,3], 1..pos*10)  = " + trav.run().apply(42L).first());

        // A counter, not a seed: number every leaf of an immutable tree.
        Tree<String> tree = new Branch<>(
                new Branch<>(new Leaf<>("a"), new Leaf<>("b")),
                new Branch<>(new Leaf<>("c"), new Leaf<>("d")));
        System.out.println("  tree before   = " + renderTree(tree));
        var labelled = labelLeaves(tree).run().apply(0);
        System.out.println("  tree after    = " + renderTree(labelled.first()));
        System.out.println("  counter ended = " + labelled.second() + " (one per leaf, threaded by flatMap)");
    }

    /*
    Section 7 - end-to-end: the purity guarantee, measured

    The claim this module has been making is that a State computation is a pure function of its starting
    state. Here it is as a test rather than an assertion:

    - Build one recipe for 100 dice rolls with sequence.
    - Run it twice from seed 42.
    - Check that the two value lists are equal, that the two final seeds are equal, and that every roll
      is inside [1, 6].

    The first two checks would fail instantly for java.util.Random, because a second call continues from
    wherever the mutable field happens to be. They pass here for the same reason 2 + 2 gives 4 twice: the
    computation is a function, and running a function does not change it.

    Practically, that is what makes randomised tests reproducible without mocking - a failing case is
    reproduced by writing down the seed.
    */
    static void endToEnd() {
        System.out.println("[Section 7] end-to-end - purity check");

        var die = between(1, 6);
        // Collections.nCopies gives 100 references to the same recipe. That is safe precisely because a
        // State is an immutable description - there is no per-instance state to get confused.
        var hundredRolls = State.sequence(Collections.nCopies(100, die));

        var run1 = hundredRolls.run().apply(42L);
        var run2 = hundredRolls.run().apply(42L);

        boolean valuesMatch = run1.first().equals(run2.first());
        boolean seedMatches = run1.second().equals(run2.second());
        boolean inRange     = run1.first().stream().allMatch(n -> n >= 1 && n <= 6);

        System.out.println("  100 rolls from seed 42, first 8: " + run1.first().subList(0, 8) + "...");
        System.out.println("  same seed -> identical sequence?   " + valuesMatch);
        System.out.println("  same seed -> identical final seed? " + seedMatches);
        System.out.println("  every roll within [1,6]?           " + inRange);
        System.out.println("  end-to-end self-check: "
                + (valuesMatch && seedMatches && inRange ? "✓" : "✗"));
    }

    public static void main(String[] args) {
        statePassingPattern();
        stateMonadIntro();
        statePrimitives();
        randomViaState();
        composingStateful();
        sequenceAndTraverse();
        endToEnd();
        System.out.println("Mod008StateMonad finished");
    }
}
