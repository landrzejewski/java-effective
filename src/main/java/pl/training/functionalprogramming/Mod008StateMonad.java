package pl.training.functionalprogramming;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

// =================================================================================================
// Section 1: The state-passing pattern
// =================================================================================================

/*
## The state-passing pattern

A *stateful* computation that should be pure expresses its state as
input and output instead of mutating a global:

```
(value, newState) = step(args, oldState)
```

Every call takes the previous state and returns the new one. There is no
shared global, no `static` field, no thread-local — just function
composition.

Disadvantage: every line of code threads the state through manually,
which is noisy. The State monad (§2) hides that plumbing.
*/

// =================================================================================================
// Section 2: State<S, A>
// =================================================================================================

/*
## State<S, A>

```
record State<S, A>(Function<S, Pair<A, S>> run) {
    <B> State<S, B> map(Function<A, B> f);
    <B> State<S, B> flatMap(Function<A, State<S, B>> f);
}
```

A `State<S, A>` *describes* a stateful computation; nothing runs until
you call `.run(initial)`, which returns `Pair<A, S>`.

- `map(f)`     — transform the produced value while keeping the state
                  flow.
- `flatMap(f)` — chain a computation whose next step depends on the value
                  the previous one produced. The state plumbs through
                  automatically.
*/

// =================================================================================================
// Section 3: Random as State<Long, Int>
// =================================================================================================

/*
## Random as State<Long, Int>

A pure 64-bit linear congruential generator: every step returns the next
seed plus the next value. Same seed → same sequence, always.

- `nextInt`     — `State<Long, Integer>` producing a non-negative int.
- `between(lo,hi)` — pick from a range; `flatMap`'d over `nextInt`.
- `nextDouble`  — similar shape, derived from `nextInt`.
*/

// =================================================================================================
// Section 4: Composing stateful computations
// =================================================================================================

/*
## Composing stateful computations

Three dice rolls expressed as a chain of `flatMap`s:

```
State<Long, List<Integer>> threeRolls =
        die.flatMap(a ->
        die.flatMap(b ->
        die.map   (c -> List.of(a, b, c))));
```

The seed flows through automatically. The user writes a sequence of
"value bindings", each pure; the State monad handles the threading.
*/

// =================================================================================================
// Section 5: sequence / traverse
// =================================================================================================

/*
## sequence / traverse

Two universal helpers when you have many `State<S, A>` and want one
`State<S, List<A>>`:

- `sequence(states)`     — run them in order, collect the produced values.
- `traverse(items, fn)`  — for each item, build a `State<S, A>` via `fn`,
                           then sequence.

These shape up naturally for "do this stateful thing N times".
*/

// =================================================================================================
// Section 6: End-to-end — same seed → same sequence
// =================================================================================================

/*
## End-to-end — same seed → same sequence

- Run `traverse` over 100 dice rolls with seed `42` twice.
- Confirm the two resulting lists are bitwise identical (purity).
- Confirm the final state (next seed) is the same in both runs.
- Confirm the rolls are within `[1, 6]`.
*/

public final class Mod008StateMonad {

    private Mod008StateMonad() {}

    // =================================================================================================
    // Pair + State
    // =================================================================================================

    public record Pair<A, B>(A first, B second) {}

    public record State<S, A>(Function<S, Pair<A, S>> run) {

        public <B> State<S, B> map(Function<A, B> f) {
            return new State<>(s -> {
                var p = run.apply(s);
                return new Pair<>(f.apply(p.first()), p.second());
            });
        }

        public <B> State<S, B> flatMap(Function<A, State<S, B>> f) {
            return new State<>(s -> {
                var p = run.apply(s);
                return f.apply(p.first()).run.apply(p.second());
            });
        }

        public static <S, A> State<S, A> pure(A value) {
            return new State<>(s -> new Pair<>(value, s));
        }

        public static <S, A> State<S, List<A>> sequence(List<State<S, A>> states) {
            // foldRight: build from the right so the head of the list comes first.
            State<S, List<A>> acc = pure(List.of());
            for (int i = states.size() - 1; i >= 0; i--) {
                State<S, A> head = states.get(i);
                State<S, List<A>> tail = acc;
                acc = head.flatMap(h ->
                        tail.map(t -> {
                            var out = new ArrayList<A>(t.size() + 1);
                            out.add(h);
                            out.addAll(t);
                            return List.copyOf(out);
                        }));
            }
            return acc;
        }

        public static <S, A, B> State<S, List<B>> traverse(List<A> items, Function<A, State<S, B>> f) {
            return sequence(items.stream().map(f).toList());
        }
    }

    // =================================================================================================
    // Random — pure LCG (java.util.Random parameters)
    // =================================================================================================

    private static final long MULTIPLIER = 0x5DEECE66DL;
    private static final long INCREMENT  = 0xBL;
    private static final long MASK       = (1L << 48) - 1;

    /** Steps the seed and returns 32 bits of pseudo-random output. */
    static State<Long, Integer> nextInt() {
        return new State<>(seed -> {
            long next = (seed * MULTIPLIER + INCREMENT) & MASK;
            int  out  = (int) (next >>> 16);
            // make non-negative
            if (out < 0) out = -(out + 1);
            return new Pair<>(out, next);
        });
    }

    static State<Long, Integer> between(int loInclusive, int hiInclusive) {
        int span = hiInclusive - loInclusive + 1;
        return nextInt().map(n -> loInclusive + Math.floorMod(n, span));
    }

    static State<Long, Double> nextDouble() {
        return nextInt().map(n -> (n & 0x7fffffff) / (double) Integer.MAX_VALUE);
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    static void statePassingPattern() {
        System.out.println("[Section 1] state-passing pattern (manual)");
        long seed = 42L;
        var p1 = nextInt().run().apply(seed);
        var p2 = nextInt().run().apply(p1.second());
        System.out.println("  seed=" + seed + " → first int  = " + p1.first() + ", new seed = " + p1.second());
        System.out.println("              → second int = " + p2.first() + ", new seed = " + p2.second());
    }

    static void stateMonadIntro() {
        System.out.println("[Section 2] State<S, A>");
        State<Long, String> labeled = nextInt().map(n -> "rolled " + n);
        System.out.println("  labeled.run(42) = " + labeled.run().apply(42L));
    }

    static void randomViaState() {
        System.out.println("[Section 3] Random as State<Long, Int>");
        var die = between(1, 6);
        var p1 = die.run().apply(42L);
        var p2 = die.run().apply(p1.second());
        System.out.println("  die from seed 42 → " + p1.first() + ", then " + p2.first());
        System.out.println("  same seed twice  → " + die.run().apply(42L).first()
                + " == " + die.run().apply(42L).first());
    }

    static void composingStateful() {
        System.out.println("[Section 4] composing stateful computations");
        var die = between(1, 6);
        State<Long, List<Integer>> threeRolls =
                die.flatMap(a ->
                die.flatMap(b ->
                die.map   (c -> List.of(a, b, c))));
        System.out.println("  threeRolls(42) = " + threeRolls.run().apply(42L).first());
    }

    static void sequenceAndTraverse() {
        System.out.println("[Section 5] sequence / traverse");
        var die = between(1, 6);
        // 5 rolls via sequence
        var fiveRolls = State.<Long, Integer>sequence(List.of(die, die, die, die, die));
        System.out.println("  sequence of 5 dice (seed 42) = " + fiveRolls.run().apply(42L).first());

        // traverse: pick a number from a position-dependent range
        var trav = State.<Long, Integer, Integer>traverse(
                List.of(1, 2, 3),
                position -> between(1, position * 10));
        System.out.println("  traverse([1,2,3], pos*10)    = " + trav.run().apply(42L).first());
    }

    static void endToEnd() {
        System.out.println("[Section 6] end-to-end — purity check");

        var die = between(1, 6);
        var rolls = State.<Long, State<Long, Integer>, Integer>traverse(
                java.util.stream.IntStream.range(0, 100).boxed().map(__ -> die).toList(),
                Function.identity());

        var run1 = rolls.run().apply(42L);
        var run2 = rolls.run().apply(42L);

        boolean valuesMatch = run1.first().equals(run2.first());
        boolean seedMatches = run1.second().equals(run2.second());
        boolean inRange     = run1.first().stream().allMatch(n -> n >= 1 && n <= 6);

        System.out.println("  100 rolls, seed=42 — first run starts with: "
                + run1.first().subList(0, 8) + "...");
        System.out.println("  same seed → identical sequence?    " + valuesMatch);
        System.out.println("  same seed → identical final seed?  " + seedMatches);
        System.out.println("  every roll in [1,6]?               " + inRange);
        System.out.println("  end-to-end self-check: " +
                (valuesMatch && seedMatches && inRange ? "✓" : "✗"));
    }

    public static void main(String[] args) {
        statePassingPattern();
        stateMonadIntro();
        randomViaState();
        composingStateful();
        sequenceAndTraverse();
        endToEnd();
        System.out.println("Mod008StateMonad finished");
    }
}
