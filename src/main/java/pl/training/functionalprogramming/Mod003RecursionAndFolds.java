package pl.training.functionalprogramming;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/*
Recursion, folds, and how to survive both on the JVM

Functional code replaces loops with recursion, and then replaces most of that recursion with two
functions: foldLeft and foldRight. Understanding this module means three separate realisations:

1. Almost every list operation you write by hand is a fold. Sum, product, length, max, reverse, map,
   filter, group-by, "find the first" - all of them are "start with a value, walk the list, combine".
   Once you see the shape you stop writing the traversal and start writing only the combining step.

2. foldLeft and foldRight differ in more than direction. foldLeft is a loop with an accumulator and runs
   in constant stack. foldRight is genuinely recursive and its stack grows with the input. Which one you
   can use is often decided by that, not by taste.

3. Java will not save you. Other functional languages eliminate tail calls so that a tail-recursive
   function compiles to a jump; the JVM does not, so a "properly" tail-recursive Java method still
   allocates a frame per call and still dies on deep input. Section 4 proves it by finding the exact
   depth at which your JVM gives up.

The fix that keeps the recursive style without the stack cost is the trampoline in Section 5, and it is
worth the effort: it is the same technique real IO and State implementations use to make deep flatMap
chains safe (see Mod010).
*/

public final class Mod003RecursionAndFolds {

    private Mod003RecursionAndFolds() {}

    // =================================================================================================
    // Folds
    // =================================================================================================

    /*
    foldLeft: start at the left with `zero`, and keep folding the next element into the accumulator.

        foldLeft(z, f, [a, b, c])  =  f(f(f(z, a), b), c)

    Note the argument order in the combining function: (accumulator, element). The accumulator comes
    first because it is being carried along from the left. This is a plain loop - constant stack, works
    on any input size.
    */
    static <A, B> B foldLeft(B zero, BiFunction<B, A, B> f, List<A> xs) {
        B acc = zero;
        for (A x : xs) acc = f.apply(acc, x);
        return acc;
    }

    /*
    foldRight: conceptually starts at the right end and works backwards.

        foldRight(z, f, [a, b, c])  =  f(a, f(b, f(c, z)))

    The argument order flips to (element, accumulator), because now the element is on the outside and the
    accumulated rest-of-the-list is on the inside.

    Written the textbook way this is a recursive method, and its stack depth equals the list length. We
    walk the indices backwards in a loop instead, which produces the identical result with no recursion
    at all. That is deliberate: the point of the module is that on the JVM you frequently have to
    *implement* the elegant recursive definition as a loop, even while keeping the recursive interface.
    */
    static <A, B> B foldRight(B zero, BiFunction<A, B, B> f, List<A> xs) {
        B acc = zero;
        for (int i = xs.size() - 1; i >= 0; i--) acc = f.apply(xs.get(i), acc);
        return acc;
    }

    // =================================================================================================
    // Five operations - two implementations each
    //
    // Read these in pairs. The recursive version on the left shows the *shape* of the operation; the
    // fold version on the right shows that the shape was never the interesting part - only the two
    // arguments to foldLeft (the starting value, and how to combine one more element) ever change.
    // =================================================================================================

    static int sumRecursive(List<Integer> xs) {
        if (xs.isEmpty()) return 0;                                   // base case: nothing left to add
        return xs.get(0) + sumRecursive(xs.subList(1, xs.size()));    // head + sum of the tail
    }
    static int sumByFold(List<Integer> xs) { return foldLeft(0, Integer::sum, xs); }

    static long productRecursive(List<Integer> xs) {
        if (xs.isEmpty()) return 1L;                                  // note: 1, not 0 - the identity
        return xs.get(0) * productRecursive(xs.subList(1, xs.size()));
    }
    static long productByFold(List<Integer> xs) {
        return foldLeft(1L, (acc, x) -> acc * x, xs);
    }

    static int lengthRecursive(List<?> xs) {
        if (xs.isEmpty()) return 0;
        return 1 + lengthRecursive(xs.subList(1, xs.size()));         // ignore the element, count it
    }
    static int lengthByFold(List<?> xs) {
        return foldLeft(0, (acc, x) -> acc + 1, xs);                  // the element is unused here too
    }

    static <A> List<A> reverseRecursive(List<A> xs) {
        if (xs.isEmpty()) return List.of();
        var rest = reverseRecursive(xs.subList(1, xs.size()));
        var out = new ArrayList<A>(rest); out.add(xs.get(0));         // head goes to the *end*
        return List.copyOf(out);
    }
    static <A> List<A> reverseByFold(List<A> xs) {
        // Reverse is the canonical foldLeft: keep pushing each new element in front of the accumulator.
        return foldLeft(List.<A>of(),
                (acc, x) -> { var c = new ArrayList<A>(); c.add(x); c.addAll(acc); return List.copyOf(c); },
                xs);
    }

    /*
    max is the one operation in this list with a wrinkle worth noticing.

    maxRecursive has no empty case - it stops at size() == 1, so calling it with an empty list throws.
    That is honest: there is no "maximum of nothing".

    maxByFold has to invent a starting value, and the only one that cannot change the answer is
    Integer.MIN_VALUE, because max(MIN_VALUE, x) == x for every int. So maxByFold(emptyList) returns
    Integer.MIN_VALUE rather than failing - a different, arguably worse, answer.

    A starting value that provably cannot change the result is called an *identity*, and the question of
    which operations have one (sum: 0, product: 1, max over int: MIN_VALUE, max over BigDecimal: none)
    is exactly what Mod009 is about.
    */
    static int maxRecursive(List<Integer> xs) {
        if (xs.size() == 1) return xs.get(0);
        return Math.max(xs.get(0), maxRecursive(xs.subList(1, xs.size())));
    }
    static int maxByFold(List<Integer> xs) {
        return foldLeft(Integer.MIN_VALUE, Integer::max, xs);
    }

    // =================================================================================================
    // Factorial trio - the same computation, three stack profiles
    // =================================================================================================

    /** Naive recursion: the multiply happens *after* the recursive call returns, so every pending
     *  multiplication holds a stack frame open. Depth n, and it dies somewhere in the low thousands. */
    static BigInteger factNaive(int n) {
        if (n == 0) return BigInteger.ONE;
        return BigInteger.valueOf(n).multiply(factNaive(n - 1));
    }

    /** A deliberately cheap tail-recursive method, used by Section 4 to find the depth at which this
     *  JVM's stack runs out. It does no allocation, so what it measures is frame count and nothing else.
     *  Textbook-tail-recursive: the recursive call is the whole return expression. */
    static int countdownTailRec(int n, int acc) {
        if (n == 0) return acc;
        return countdownTailRec(n - 1, acc + 1);
    }

    /** Tail-recursive in *shape*: the recursive call is the last thing the method does, with the running
     *  product carried in an accumulator so there is nothing to do after it returns. A language with
     *  tail-call elimination compiles this to a jump and it runs in one frame forever.
     *  Java does not. It dies at essentially the same depth as factNaive. Section 4 measures it. */
    static BigInteger factTailRec(int n, BigInteger acc) {
        if (n == 0) return acc;
        return factTailRec(n - 1, acc.multiply(BigInteger.valueOf(n)));
    }

    // =================================================================================================
    // Trampoline
    //
    // The trick in three types:
    //
    //   Trampoline<T>   "a computation that is either finished or has one more step to do"
    //   Done<T>(value)  finished - here is the answer
    //   More<T>(next)   not finished - here is a Supplier that will do the next step *when asked*
    //
    // The recursive method never calls itself. It RETURNS a description of the next call, wrapped in a
    // Supplier so nothing happens yet. Its own frame is then free to pop.
    //
    // runUntilDone is the engine: one loop, one frame, pulling each thunk and replacing `step` with
    // whatever came back, until a Done shows up. The recursion has been moved off the call stack and
    // onto the heap, where there is far more room.
    // =================================================================================================

    sealed interface Trampoline<T> permits Done, More {
        default T runUntilDone() {
            Trampoline<T> step = this;
            // Each iteration runs exactly one step. m.next.get() is where the "recursive call" happens,
            // but because it returns to this loop rather than nesting, the stack never grows.
            while (step instanceof More<T> m) step = m.next.get();
            return ((Done<T>) step).value;
        }
    }
    record Done<T>(T value) implements Trampoline<T> {}
    record More<T>(Supplier<Trampoline<T>> next) implements Trampoline<T> {}

    /*
    Compare against factTailRec above. The body is nearly identical - the accumulator, the decrement, the
    base case. The only change is that where factTailRec *calls* itself, this *returns* `new More<>(() ->
    ...)`. That one change is the whole technique.
    */
    static Trampoline<BigInteger> factTrampolined(int n, BigInteger acc) {
        if (n == 0) return new Done<>(acc);
        return new More<>(() -> factTrampolined(n - 1, acc.multiply(BigInteger.valueOf(n))));
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    /*
    Section 1 - recursion as the functional loop

    Functional code typically replaces for and while with recursion, and every such function has the same
    two-part skeleton:

    - a base case saying when to stop (empty list, n == 0),
    - a recursive case that peels one element off, calls itself on what is left, and combines that
      result with the element it peeled off.

    The shape of the recursion mirrors the shape of the data. A linked list splits into head and tail, so
    list functions recurse once. A binary tree splits into left and right, so tree functions recurse
    twice (see Mod004).

    Recursion expresses these operations very directly, but Java's stack is finite: the default thread
    stack is on the order of 1-2 MB depending on the platform, which is tens of thousands of frames for a
    trivial method and far fewer for one with a real frame. Section 4 measures where THIS JVM gives up,
    and the number is a resource limit, not a language guarantee. For deep input you need one of: an
    ordinary loop, an accumulator plus a trampoline (Section 5), or a fold that is internally a loop
    (Section 2).
    */
    static void recursionAsLoop() {
        System.out.println("[Section 1] recursion as the FP loop");
        var xs = List.of(1, 2, 3, 4, 5);
        System.out.println("  sumRecursive([1..5]) = " + sumRecursive(xs));
        System.out.println("  reverseRecursive     = " + reverseRecursive(xs));
    }

    /*
    Section 2 - foldLeft / foldRight as universal recursion templates

    Both folds collapse a list into a single value, and both take exactly two things: a starting value
    and a way to combine the accumulator with one more element.

        foldLeft(z, f, [a,b,c])  =  f(f(f(z, a), b), c)      combining function is (acc, element)
        foldRight(z, f, [a,b,c]) =  f(a, f(b, f(c, z)))      combining function is (element, acc)

    The claim worth internalising: sum, product, length, min, max, reverse, map, filter, zipWith,
    group-by and "contains" are all folds. They differ only in the starting value and the combining
    function. Once you see that, you stop writing traversals - the traversal is in the fold, and you
    supply two arguments.

    This matters beyond elegance. Because the traversal lives in one place, it can be optimised, made
    parallel, or made lazy without touching a single caller. That is precisely how Stream.reduce works,
    and why a parallel stream can use the same reduce you wrote for the sequential one - provided your
    combining function is associative, which is Mod009.
    */
    static void foldsAsTemplates() {
        System.out.println("[Section 2] folds as universal templates");
        var xs = List.of(1, 2, 3, 4, 5);
        System.out.println("  foldLeft  sum     = " + foldLeft(0, Integer::sum, xs));
        // Reverse-by-foldRight: append each element behind the already-built rest.
        System.out.println("  foldRight reverse = " +
                foldRight(List.<Integer>of(),
                        (x, acc) -> { var c = new ArrayList<Integer>(acc); c.add(x); return List.copyOf(c); },
                        xs));
    }

    /*
    Section 3 - left vs right: how to choose

    The demo below makes the difference visible with string concatenation, which is associative but not
    commutative, so the two directions produce visibly different output:

        foldLeft("|", (acc, x) -> acc + x, [a,b,c,d])   builds  ((((| + a) + b) + c) + d)  =  "|abcd"
        foldRight("|", (x, acc) -> x + acc, [a,b,c,d])  builds  (a + (b + (c + (d + |))))  =  "abcd|"

    The seed ends up on the left in one and on the right in the other. That is the whole difference in
    behaviour - but there is a second difference in cost:

    - foldLeft is naturally tail-recursive. It carries an accumulator forward and never needs to come
      back, so it is a loop and runs in constant stack on any size of input.
    - foldRight is not. Each step must wait for the result of the rest of the list before it can combine,
      so N pending frames pile up. Functional languages get away with it through laziness (the pending
      work is a thunk on the heap, not a frame). In Java you either reverse the list and use foldLeft, or
      walk backwards by index, which is what foldRight above actually does.

    Practical rule: reach for foldLeft. Reach for foldRight only when the operation genuinely needs the
    rightmost element combined first - building a list front-to-back is the usual case, because prepending
    is cheap and appending is not (Mod004).
    */
    static void leftVsRight() {
        System.out.println("[Section 3] left vs right");
        var xs = List.of("a", "b", "c", "d");
        String left  = foldLeft("|", (acc, x) -> acc + x, xs);   // seed ends up on the left
        String right = foldRight("|", (x, acc) -> x + acc, xs);  // seed ends up on the right
        System.out.println("  foldLeft  = " + left  + "   (((( | +a)+b)+c)+d )");
        System.out.println("  foldRight = " + right + "   ( a+(b+(c+(d+ | ))))");
    }

    /*
    Section 4 - Java has no tail-call optimisation, and here is the proof

    A tail call is a call in the final position of a method, with nothing left to do afterwards:

        static int factTr(int n, int acc) {
            if (n == 0) return acc;
            return factTr(n - 1, acc * n);   // tail call: the result is returned unchanged
        }

    Because nothing happens after the call returns, the current frame is dead weight - a compiler is free
    to reuse it and turn the recursion into a jump. Scala, Kotlin (with `tailrec`), Scheme and Haskell all
    do this. The JVM does not: there has been a proposal for it for many years, and every production JVM
    still allocates a fresh frame per call.

    So the tail-recursive *shape* buys you nothing on stack depth in Java. To make that concrete rather
    than theoretical, this section searches for the depth at which your JVM actually gives up, and prints
    it. The number varies with -Xss, the JIT and the frame size, which is itself the point: it is a
    resource limit, not a language guarantee, and you cannot write code that relies on it.

    The two ways out are an ordinary loop (idiomatic Java) or a trampoline (Section 5), which keeps the
    recursive style while bounding the stack to one frame.
    */
    static void noTcoInJava() {
        System.out.println("[Section 4] no TCO in Java");

        // Climb the depth until the stack gives out. Deterministic on every JVM: it always breaks
        // somewhere, and we report where rather than hoping a fixed guess happened to be big enough.
        int lastSurvived = 0;
        for (int depth = 1_000; depth <= 8_000_000; depth *= 2) {
            try {
                countdownTailRec(depth, 0);
                lastSurvived = depth;
            } catch (StackOverflowError soe) {
                System.out.println("  countdownTailRec survived depth " + lastSurvived
                        + " but died at depth " + depth);
                System.out.println("  the recursive call is in tail position - the JVM allocated a frame anyway");
                System.out.println("  (the exact number depends on -Xss and the JIT, which is the point:");
                System.out.println("   stack depth is a resource limit, never a language guarantee)");
                return;
            }
        }
        System.out.println("  no overflow up to 8M frames (a very large -Xss); the frames are still allocated");
    }

    /*
    Section 5 - trampolines

    A trampoline converts recursion into iteration without giving up the recursive way of writing things.
    Three moving parts:

    - A recursive step returns a *description* of what to do next instead of doing it: More(() -> ...).
      The Supplier is the key - building it runs nothing, so the current method returns immediately and
      its stack frame is released.
    - The final step returns Done(value).
    - A driver loop (runUntilDone) repeatedly asks the current description for the next one, until a Done
      appears. It is one while loop in one stack frame.

    The chain of pending calls still exists - it just lives on the heap as a sequence of short-lived
    Supplier objects rather than on the stack as frames. The heap is enormous and growable; the stack is
    neither. That is the entire trade.

    Compare factTailRec with factTrampolined above: same accumulator, same base case, same arithmetic.
    The only difference is `return factTrampolined(...)` becoming `return new More<>(() ->
    factTrampolined(...))`. Section 4 showed factTailRec dying in the thousands; below, the trampolined
    version computes 50000! - a 213,237-digit number - without touching the stack.

    This is not a toy technique. Production IO and State implementations (Cats Effect, ZIO, Vavr) all
    trampoline their flatMap chains for exactly this reason - see the note in Mod010 Section 2.
    */
    static void trampolinesDemo() {
        System.out.println("[Section 5] trampolines");

        // First, show the naive version failing at a depth the trampoline handles easily.
        try {
            factNaive(50_000);
            System.out.println("  factNaive(50000) somehow survived (unusually large -Xss)");
        } catch (StackOverflowError soe) {
            System.out.println("  factNaive(50000)       -> StackOverflowError");
        }

        var bigN = 50_000;
        BigInteger result = factTrampolined(bigN, BigInteger.ONE).runUntilDone();
        System.out.println("  factTrampolined(50000) -> " + result.toString().length()
                + "-digit number, no stack growth");
    }

    /*
    Section 6 - end-to-end self-check

    Two families of assertions:

    - For five list operations (sum, product, length, reverse, max), the direct recursive implementation
      and the fold-based one must agree. They are different code with the same meaning, which is the
      module's central claim made testable.
    - The trampolined factorial must produce 50000! correctly. We check its digit count against 213237,
      a value fixed by mathematics rather than by this implementation, so the check would catch a
      trampoline that silently dropped or repeated a step.
    */
    static void endToEnd() {
        System.out.println("[Section 6] end-to-end self-check");
        var xs = List.of(3, 1, 4, 1, 5, 9, 2, 6);

        boolean sumOk  = sumRecursive(xs)     == sumByFold(xs);
        boolean prodOk = productRecursive(xs) == productByFold(xs);
        boolean lenOk  = lengthRecursive(xs)  == lengthByFold(xs);
        boolean revOk  = reverseRecursive(xs).equals(reverseByFold(xs));
        boolean maxOk  = maxRecursive(xs)     == maxByFold(xs);

        System.out.println("  sum:     " + sumByFold(xs)     + (sumOk  ? " ✓" : " ✗"));
        System.out.println("  product: " + productByFold(xs) + (prodOk ? " ✓" : " ✗"));
        System.out.println("  length:  " + lengthByFold(xs)  + (lenOk  ? " ✓" : " ✗"));
        System.out.println("  reverse: " + reverseByFold(xs) + (revOk  ? " ✓" : " ✗"));
        System.out.println("  max:     " + maxByFold(xs)     + (maxOk  ? " ✓" : " ✗"));

        // The trampolined factorial succeeds at a depth that kills both stack-based versions.
        BigInteger big = factTrampolined(50_000, BigInteger.ONE).runUntilDone();
        boolean trampolineOk = big.toString().length() == 213_237;   // 50000! has exactly this many digits
        System.out.println("  factorial(50000) digits = " + big.toString().length()
                + " (expected 213237)" + (trampolineOk ? " ✓" : " ✗"));

        boolean allOk = sumOk && prodOk && lenOk && revOk && maxOk && trampolineOk;
        System.out.println("  all self-checks pass? " + allOk);
    }

    public static void main(String[] args) {
        recursionAsLoop();
        foldsAsTemplates();
        leftVsRight();
        noTcoInJava();
        trampolinesDemo();
        endToEnd();
        System.out.println("Mod003RecursionAndFolds finished");
    }
}
