package pl.training.functionalprogramming;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class Mod003RecursionAndFolds {

    private Mod003RecursionAndFolds() {}

    // =================================================================================================
    // Folds
    // =================================================================================================

    static <A, B> B foldLeft(B zero, BiFunction<B, A, B> f, List<A> xs) {
        B acc = zero;
        for (A x : xs) acc = f.apply(acc, x);
        return acc;
    }

    /** foldRight implemented via reversed foldLeft to avoid Java's stack limits. */
    static <A, B> B foldRight(B zero, BiFunction<A, B, B> f, List<A> xs) {
        B acc = zero;
        for (int i = xs.size() - 1; i >= 0; i--) acc = f.apply(xs.get(i), acc);
        return acc;
    }

    // =================================================================================================
    // Five operations — two implementations each
    // =================================================================================================

    static int sumRecursive(List<Integer> xs) {
        if (xs.isEmpty()) return 0;
        return xs.get(0) + sumRecursive(xs.subList(1, xs.size()));
    }
    static int sumByFold(List<Integer> xs) { return foldLeft(0, Integer::sum, xs); }

    static long productRecursive(List<Integer> xs) {
        if (xs.isEmpty()) return 1L;
        return xs.get(0) * productRecursive(xs.subList(1, xs.size()));
    }
    static long productByFold(List<Integer> xs) {
        return foldLeft(1L, (acc, x) -> acc * x, xs);
    }

    static int lengthRecursive(List<?> xs) {
        if (xs.isEmpty()) return 0;
        return 1 + lengthRecursive(xs.subList(1, xs.size()));
    }
    static int lengthByFold(List<?> xs) {
        return foldLeft(0, (acc, x) -> acc + 1, xs);
    }

    static <A> List<A> reverseRecursive(List<A> xs) {
        if (xs.isEmpty()) return List.of();
        var rest = reverseRecursive(xs.subList(1, xs.size()));
        var out = new ArrayList<A>(rest); out.add(xs.get(0));
        return List.copyOf(out);
    }
    static <A> List<A> reverseByFold(List<A> xs) {
        return foldLeft(List.<A>of(),
                (acc, x) -> { var c = new ArrayList<A>(); c.add(x); c.addAll(acc); return List.copyOf(c); },
                xs);
    }

    static int maxRecursive(List<Integer> xs) {
        if (xs.size() == 1) return xs.get(0);
        return Math.max(xs.get(0), maxRecursive(xs.subList(1, xs.size())));
    }
    static int maxByFold(List<Integer> xs) {
        return foldLeft(Integer.MIN_VALUE, Integer::max, xs);
    }

    // =================================================================================================
    // Factorial trio
    // =================================================================================================

    /** Naive recursion — fine for small n, blows the stack around 5–10k in Java. */
    static BigInteger factNaive(int n) {
        if (n == 0) return BigInteger.ONE;
        return BigInteger.valueOf(n).multiply(factNaive(n - 1));
    }

    /** Tail-recursive in shape — but Java does not eliminate the tail call! Same fate. */
    static BigInteger factTailRec(int n, BigInteger acc) {
        if (n == 0) return acc;
        return factTailRec(n - 1, acc.multiply(BigInteger.valueOf(n)));
    }

    // =================================================================================================
    // Trampoline
    // =================================================================================================

    sealed interface Trampoline<T> permits Done, More {
        default T runUntilDone() {
            Trampoline<T> step = this;
            while (step instanceof More<T> m) step = m.next.get();
            return ((Done<T>) step).value;
        }
    }
    record Done<T>(T value) implements Trampoline<T> {}
    record More<T>(Supplier<Trampoline<T>> next) implements Trampoline<T> {}

    static Trampoline<BigInteger> factTrampolined(int n, BigInteger acc) {
        if (n == 0) return new Done<>(acc);
        return new More<>(() -> factTrampolined(n - 1, acc.multiply(BigInteger.valueOf(n))));
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    /*
    Recursion as the FP loop

    Functional code typically replaces for and while with recursion:

    - A base case says when to stop (empty list, n == 0).
    - A recursive case breaks the input into a smaller piece and combines the result of the recursive call with the
      local data.

    The shape of the recursion mirrors the shape of the data structure: linked lists fold by head/tail, trees fold
    by left/right subtree.

    Recursion is a powerful elimination form, but Java's stack is finite. For deep recursion you need either
    explicit iteration, accumulator recursion (§3) or a trampoline (§5).
    */
    static void recursionAsLoop() {
        System.out.println("[Section 1] recursion as the FP loop");
        var xs = List.of(1, 2, 3, 4, 5);
        System.out.println("  sumRecursive([1..5]) = " + sumRecursive(xs));
        System.out.println("  reverseRecursive    = " + reverseRecursive(xs));
    }

    /*
    foldLeft / foldRight as universal recursion templates

    Both folds reduce a list to a single value.

    - foldLeft(z, f, list) runs f(z, x1) first, then f(_, x2), … — left-to-right, accumulator-passing.
    - foldRight(z, f, list) runs f(xN, z) first, then f(xN-1, _), … — right-to-left, building the result top-down.

    Sum, product, length, max, min, reverse, map, filter, zipWith — every one of them is a fold. Recognising the
    fold shape teaches you to think in operations over the whole structure rather than indexing.
    */
    static void foldsAsTemplates() {
        System.out.println("[Section 2] folds as universal templates");
        var xs = List.of(1, 2, 3, 4, 5);
        System.out.println("  foldLeft  sum     = " + foldLeft(0, Integer::sum, xs));
        System.out.println("  foldRight reverse = " +
                foldRight(List.<Integer>of(),
                        (x, acc) -> { var c = new ArrayList<Integer>(acc); c.add(x); return List.copyOf(c); },
                        xs));
    }

    /*
    Left vs right folding — choosing one

    - foldLeft is naturally tail-recursive. It walks the list with an accumulator; in a language with TCO it runs
      in constant stack.
    - foldRight is not. Each recursive call wraps the previous result in a function application, so the stack grows
      with N. In FP languages you fix it with laziness; in Java you fix it by converting to foldLeft on the reversed
      list or by trampolining.
    - Pick foldLeft when the operation is associative or you only need an accumulator; pick foldRight when you need
      the right-most element to combine first (e.g., building a list head-first).
    */
    static void leftVsRight() {
        System.out.println("[Section 3] left vs right");
        var xs = List.of("a", "b", "c", "d");
        // foldLeft builds "((((|+a)+b)+c)+d)" → "|abcd"
        String left = foldLeft("|", (acc, x) -> acc + x, xs);
        // foldRight builds "(a+(b+(c+(d+|))))" → "abcd|"
        String right = foldRight("|", (x, acc) -> x + acc, xs);
        System.out.println("  foldLeft  = " + left);
        System.out.println("  foldRight = " + right);
    }

    /*
    Java's missing tail-call optimisation

    Even a perfectly tail-recursive Java method blows the stack on deep inputs:

    static int factTr(int n, int acc) {
        if (n == 0) return acc;
        return factTr(n - 1, acc * n);   // tail call — but JVM does NOT eliminate it
    }
    factTr(50_000, 1);                   // → StackOverflowError

    The JVM has had a TCO proposal sitting around for years; in production JVMs the call frame is allocated on every
    recursive call. The standard fixes are:

    - Rewrite the recursion as a for loop (idiomatic Java).
    - Use a trampoline (§5) to keep the recursive shape but bound the stack to a single frame.
    */
    static void noTcoInJava() {
        System.out.println("[Section 4] no TCO in Java");
        // Demonstrate with a manageable size; show that tail-recursive shape does not save the stack.
        try {
            factTailRec(20_000, BigInteger.ONE);
            System.out.println("  factTailRec(20000) survived this run (depends on -Xss)");
        } catch (StackOverflowError soe) {
            System.out.println("  factTailRec(20000) → StackOverflowError (Java does not eliminate tail calls)");
        }
    }

    /*
    Trampolines

    A trampoline turns recursion into iteration:

    - A recursive call returns not the final value, but a thunk (Supplier) that, when run, performs the next step.
    - The driver loop pulls the thunk, runs it, gets either another thunk or a final value, and repeats until done.
    - The recursive shape is preserved — the code looks like recursion — but the actual call stack is a single
      frame.

    Below: Trampoline<T> is a tiny three-line API. factorial(50_000) runs to completion as a BigInteger without
    StackOverflow.
    */
    static void trampolinesDemo() {
        System.out.println("[Section 5] trampolines");
        var bigN = 50_000;
        BigInteger result = factTrampolined(bigN, BigInteger.ONE).runUntilDone();
        System.out.println("  factTrampolined(" + bigN + ") = " + result.toString().length() + " digit number");
    }

    /*
    End-to-end self-check

    - For five list operations (sum, product, length, reverse, max), build two implementations: a direct recursive
      one and a fold-based one. Assert they agree on a small list.
    - For factorial, build a naive recursive BigInteger version, a tail-recursive accumulating version (still blows
      the stack at 50_000 in Java!), and a trampolined version that does not.
    - Print everything and confirm the trampolined factorial produced the expected number of digits.
    */
    static void endToEnd() {
        System.out.println("[Section 6] end-to-end self-check");
        var xs = List.of(3, 1, 4, 1, 5, 9, 2, 6);

        boolean sumOk     = sumRecursive(xs)     == sumByFold(xs);
        boolean prodOk    = productRecursive(xs) == productByFold(xs);
        boolean lenOk     = lengthRecursive(xs)  == lengthByFold(xs);
        boolean revOk     = reverseRecursive(xs).equals(reverseByFold(xs));
        boolean maxOk     = maxRecursive(xs)     == maxByFold(xs);

        System.out.println("  sum:     " + sumByFold(xs)     + (sumOk  ? " ✓" : " ✗"));
        System.out.println("  product: " + productByFold(xs) + (prodOk ? " ✓" : " ✗"));
        System.out.println("  length:  " + lengthByFold(xs)  + (lenOk  ? " ✓" : " ✗"));
        System.out.println("  reverse: " + reverseByFold(xs) + (revOk  ? " ✓" : " ✗"));
        System.out.println("  max:     " + maxByFold(xs)     + (maxOk  ? " ✓" : " ✗"));

        // Trampolined factorial succeeds even at 50k.
        BigInteger big = factTrampolined(50_000, BigInteger.ONE).runUntilDone();
        boolean trampolineOk = big.toString().length() == 213_237; // 50000! has this many digits
        System.out.println("  factorial(50000) digits = " + big.toString().length()
                + " (expected 213237) " + (trampolineOk ? "✓" : "✗"));

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
