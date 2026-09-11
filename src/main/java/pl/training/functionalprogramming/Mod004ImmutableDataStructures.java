package pl.training.functionalprogramming;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/*
Persistent data structures - how immutability stops being unaffordable

The obvious objection to Mod001 is cost. If nothing can be modified, then every "change" is a copy, and
copying a million-element list to change one entry is absurd. If that were really the trade, functional
programming would be a curiosity.

It is not the trade, and this module shows why.

- An *ephemeral* data structure is modified in place: ArrayList.add, HashMap.put. There is exactly one
  version of it, and after each modification the previous version no longer exists anywhere.
- A *persistent* data structure keeps every previous version alive when you "update" it. The update
  returns a new version, and the old one remains valid and unchanged.
- The trick that makes this cheap is *structural sharing*: the new version reuses as much of the old
  one's internals as it possibly can, and copies only what actually differs. Because everything is
  immutable, sharing is safe - nobody can reach through the shared part and change it underneath you.

The two structures here (a cons-cell list and a binary tree) are the simplest shapes that demonstrate
sharing, and they are what you would build by hand. Production persistent collections - Clojure's and
Scala's Vector, Vavr, Eclipse Collections - use much cleverer internals (32-way branching hash-array
mapped tries) to get near-constant time on random access, but the underlying idea is exactly this one:
rebuild the path you touched, share everything else.

The honest caveat, which Section 5 measures: sharing makes *some* operations cheap, not all of them. A
cons list gives you O(1) prepend and O(n) append. Choosing the right persistent structure for your access
pattern matters just as much as it does for mutable ones.
*/

public final class Mod004ImmutableDataStructures {

    private Mod004ImmutableDataStructures() {}

    // =================================================================================================
    // FList<A> - a persistent singly-linked list
    //
    // Two shapes and nothing else:
    //   Nil            the empty list
    //   Cons(head, tail)   one element, followed by the rest (which is itself an FList)
    //
    // "Cons" is short for "construct" and dates back to Lisp. Because the interface is sealed, those two
    // are the *only* possibilities, so every operation below is a complete case analysis - the compiler
    // will tell you if you forget one.
    // =================================================================================================

    public sealed interface FList<A> permits Nil, Cons {

        default boolean isEmpty()              { return this instanceof Nil<A>; }
        default int size()                     { return foldLeft(0, (acc, x) -> acc + 1); }

        /* O(1), and the whole point of the structure: the new list *is* a new Cons cell whose tail is
           this exact list object. Nothing is copied and nothing is traversed. */
        default FList<A> prepend(A x)          { return new Cons<>(x, this); }

        /* An iterative foldLeft - the loop walks the cells, so it is constant-stack even on long lists.
           Everything below that needs to traverse is written in terms of this. */
        default <B> B foldLeft(B zero, BiFunction<B, A, B> f) {
            B acc = zero; FList<A> here = this;
            while (here instanceof Cons<A>(A head, FList<A> tail)) {
                acc = f.apply(acc, head); here = tail;
            }
            return acc;
        }

        /* foldRight, done by folding left over the reversed list. Same reasoning as Mod003: the natural
           definition is recursive and would grow the stack, so we reverse once (O(n)) and loop. */
        default <B> B foldRight(B zero, BiFunction<A, B, B> f) {
            return reverse().foldLeft(zero, (acc, x) -> f.apply(x, acc));
        }

        /* Build the result back-to-front with prepend, which is why this is foldRight rather than
           foldLeft: the last element must be added to the accumulator first. */
        default <B> FList<B> map(Function<A, B> f) {
            return foldRight(FList.<B>empty(), (x, acc) -> acc.prepend(f.apply(x)));
        }

        default FList<A> filter(Predicate<A> p) {
            return foldRight(FList.<A>empty(), (x, acc) -> p.test(x) ? acc.prepend(x) : acc);
        }

        /* reverse in one left-to-right pass: repeatedly push the next element onto the front of the
           accumulator. O(n) time, and it allocates n new cells - the original list is untouched. */
        default FList<A> reverse() {
            return foldLeft(empty(), (FList<A> acc, A x) -> acc.prepend(x));
        }

        /* concat: every cell of the LEFT list has to be rebuilt, because a Cons cell is immutable and
           the last one needs a different tail. The RIGHT list is shared wholesale - not one cell of it
           is copied. Hence O(n) in the left operand and O(1) in the right. */
        default FList<A> concat(FList<A> other) {
            return foldRight(other, (x, acc) -> acc.prepend(x));
        }

        /* append is just concat with a one-element list, which is exactly why it costs O(n): adding one
           element to the end rebuilds all n cells in front of it. Compare prepend, which rebuilds none.
           This asymmetry is the defining property of a cons list. */
        default FList<A> append(A x) {
            return concat(new Cons<>(x, empty()));
        }

        default java.util.List<A> toList() {
            var out = new java.util.ArrayList<A>(); FList<A> here = this;
            while (here instanceof Cons<A>(A head, FList<A> tail)) { out.add(head); here = tail; }
            return java.util.List.copyOf(out);
        }

        /* One shared empty instance for every element type. This is safe *only* because Nil holds no
           data - there is nothing in it that could be of the wrong type - which is why the unchecked
           cast is sound rather than merely convenient. */
        @SuppressWarnings("unchecked")
        static <A> FList<A> empty() { return (FList<A>) Nil.INSTANCE; }

        @SafeVarargs
        static <A> FList<A> of(A... xs) {
            // Built back-to-front, because prepend is the cheap end.
            FList<A> acc = empty();
            for (int i = xs.length - 1; i >= 0; i--) acc = acc.prepend(xs[i]);
            return acc;
        }
    }
    public record Nil<A>() implements FList<A> {
        static final Nil<?> INSTANCE = new Nil<>();
    }
    public record Cons<A>(A head, FList<A> tail) implements FList<A> {}

    // =================================================================================================
    // Tree<A> - a persistent binary tree
    //
    // Leaf(value) holds data; Branch(left, right) holds two subtrees. Where a list recurses once (on the
    // tail), a tree recurses twice (on both children) - the code shape always follows the data shape.
    // =================================================================================================

    public sealed interface Tree<A> permits Leaf, Branch {
        /** Number of leaves. Two cases, so two branches - and `sealed` means the compiler checks that. */
        default int size() {
            return switch (this) {
                case Leaf<A> _                       -> 1;
                case Branch<A>(Tree<A> l, Tree<A> r) -> l.size() + r.size();
            };
        }
        /** Rebuilds every Branch (they must point at the new children) but the *shape* is preserved. */
        default <B> Tree<B> mapValues(Function<A, B> f) {
            return switch (this) {
                case Leaf<A>(A v)                    -> new Leaf<>(f.apply(v));
                case Branch<A>(Tree<A> l, Tree<A> r) -> new Branch<>(l.mapValues(f), r.mapValues(f));
            };
        }
        /** Replace one leaf, identified by a path of directions. This is where tree sharing shows up:
         *  only the Branches along the path are rebuilt; every subtree hanging off that path is reused
         *  by reference. For a balanced tree of n leaves that is log2(n) new nodes instead of n. */
        default Tree<A> replaceAt(java.util.List<Boolean> goRight, A value) {
            if (goRight.isEmpty()) return new Leaf<>(value);
            if (this instanceof Branch<A>(Tree<A> l, Tree<A> r)) {
                var rest = goRight.subList(1, goRight.size());
                return goRight.get(0)
                        ? new Branch<>(l, r.replaceAt(rest, value))   // left subtree shared as-is
                        : new Branch<>(l.replaceAt(rest, value), r);  // right subtree shared as-is
            }
            return this;
        }
    }
    public record Leaf<A>(A value)                       implements Tree<A> {}
    public record Branch<A>(Tree<A> left, Tree<A> right) implements Tree<A> {}

    // =================================================================================================
    // Sections
    // =================================================================================================

    /*
    Section 1 - persistent vs ephemeral, and the cons-cell list

    The type in three lines:

        sealed interface FList<A> permits Nil, Cons {}
        record Nil<A>()                       implements FList<A> {}
        record Cons<A>(A head, FList<A> tail)  implements FList<A> {}

    A Cons cell holds one element and a reference to the rest of the list. Since a record's fields are
    final, no cell can ever be changed after it is built - which is the property everything else depends
    on.

    The consequence to notice in the demo below: after b = a.prepend(0), both a and b are usable, both
    are correct, and both report their own size. With an ArrayList there would be one list and one
    answer. Here there are two versions of the data alive at the same time, and no copying happened to
    make that true.
    */
    static void persistentVsEphemeral() {
        System.out.println("[Section 1] cons-cell list: two versions, one structure");
        FList<Integer> a = FList.of(1, 2, 3);
        FList<Integer> b = a.prepend(0);
        System.out.println("  a       = " + a.toList());
        System.out.println("  b       = " + b.toList()  + " (a, with 0 prepended)");
        System.out.println("  a.size  = " + a.size() + ", b.size = " + b.size()
                + "  -> both versions still exist and disagree, which is the point");
    }

    /*
    Section 2 - structural sharing

    When you write b = a.prepend(x), exactly one new object is allocated: a Cons cell holding x, whose
    tail field points at the *same object* a refers to.

        a:            1 -> 2 -> 3 -> Nil
                      ^
        b:   0 -------+          (b.tail() IS a - the same reference, not a copy)

    So b is one object bigger than a, not four objects bigger. The demo checks this with ==, which is
    reference equality - it is asserting that no copy was made, not merely that the contents match.

    Nothing can go wrong through the shared part, because nothing can change it. That is the deal
    immutability makes: you give up in-place mutation, and in exchange aliasing stops being dangerous, so
    sharing becomes free.

    Inserting in the *middle* follows the same rule: the prefix in front of the insertion point must be
    rebuilt (those cells need different tails), and everything from the insertion point onwards is
    shared. The cost is therefore proportional to the index you touch, not to the length of the list.
    */
    static void structuralSharing() {
        System.out.println("[Section 2] structural sharing");
        FList<Integer> a = FList.of(1, 2, 3);
        FList<Integer> b = a.prepend(0);

        Cons<Integer> bCons = (Cons<Integer>) b;
        boolean sharing = bCons.tail() == a;   // == is deliberate: reference identity, not equality
        System.out.println("  b.tail() == a (same object, no copy)? " + sharing);
        System.out.println("  b added exactly one cell to reach length " + b.size());
    }

    /*
    Section 3 - trees share too, and share better

    A tree splits into two children instead of one tail, and that changes the economics. Replacing one
    leaf rebuilds only the nodes on the path from the root down to that leaf; every subtree hanging off
    that path is reused by reference.

    For a balanced tree with n leaves the path is log2(n) long, so an "update" allocates about log2(n)
    nodes instead of n. That logarithmic behaviour is why every serious persistent collection is a tree
    underneath, even the ones that present themselves as vectors and hash maps.

    The demo replaces one leaf and then checks, with ==, that the untouched subtree is literally the same
    object in both the old and the new tree.
    */
    static void treeDemo() {
        System.out.println("[Section 3] tree");
        Tree<Integer> left  = new Branch<>(new Leaf<>(1), new Leaf<>(2));
        Tree<Integer> tree  = new Branch<>(left, new Leaf<>(3));
        System.out.println("  size = " + tree.size());
        System.out.println("  mapValues(*10) preserves shape? "
                + (tree.size() == tree.mapValues(x -> x * 10).size()));

        // Replace the leaf reached by going right from the root. The left subtree must be shared.
        Tree<Integer> updated = tree.replaceAt(java.util.List.of(true), 99);
        boolean leftShared = ((Branch<Integer>) updated).left() == left;
        System.out.println("  after replacing the right leaf, is the left subtree the same object? "
                + leftShared);
    }

    /*
    Section 4 - the cost model, and why you must know it

      Operation        Time    What is shared
      ---------------- ------- --------------------------------------------------
      prepend(x)       O(1)    the entire old list, as the new cell's tail
      head / tail      O(1)    nothing to share - just field access
      append(x)        O(n)    nothing; all n cells in front are rebuilt
      concat(other)    O(n)    the whole right operand; the left is rebuilt
      map / filter     O(n)    nothing; a full new spine, with old elements reused
      size             O(n)    there is no length field - it is a fold

    The asymmetry between prepend and append is the single most important line in that table, and it is
    not an implementation weakness - it follows from the shape. A Cons cell points forward at its tail,
    so putting something at the front requires one new cell; putting something at the back requires a new
    final cell, which requires a new second-to-last cell to point at it, and so on all the way to the
    head.

    The practical consequences: build lists by prepending and reverse once at the end (Mod003's fold
    idiom), and never index into a cons list in a loop. When you need random access, use a persistent
    Vector (Vavr, Clojure) built on a wide branching tree instead - the ideas from Section 3, scaled up.

    The demo times 100k prepends against 2k appends. Watch the per-element cost, not the totals: the
    append loop does 50x fewer operations and still takes far longer, because it is quadratic.
    */
    static void costModel() {
        System.out.println("[Section 4] cost model - prepend O(1) vs append O(n)");

        long t1 = System.nanoTime();
        FList<Integer> byPrepend = FList.empty();
        for (int i = 0; i < 100_000; i++) byPrepend = byPrepend.prepend(i);
        long prependMs = (System.nanoTime() - t1) / 1_000_000;
        System.out.println("  100000 prepends -> " + prependMs + " ms, size = " + byPrepend.size());

        long t2 = System.nanoTime();
        FList<Integer> byAppend = FList.empty();
        for (int i = 0; i < 2_000; i++) byAppend = byAppend.append(i);
        long appendMs = (System.nanoTime() - t2) / 1_000_000;
        System.out.println("  2000 appends    -> " + appendMs + " ms, size = " + byAppend.size()
                + "   (50x fewer elements, and quadratic)");

        System.out.println("  map/filter still work fine, they are single O(n) passes:");
        System.out.println("    of(1..6).filter(even).map(*10) = "
                + FList.of(1, 2, 3, 4, 5, 6).filter(x -> x % 2 == 0).map(x -> x * 10).toList());
    }

    /*
    Section 5 - end-to-end: 100 versions alive at once, none of them mutated

    Build 100 successive versions of a list by repeatedly prepending, and keep a reference to every one
    of them. Then verify three things:

    - every intermediate version still has the length it had when it was created - no later "update"
      reached back and changed an earlier version,
    - the newest version's tail is the *same object* as the previous version, proving no copying,
    - different versions are genuinely different objects.

    This is the pay-off of structural sharing stated as a number: those 100 versions occupy 99 cons cells
    in total (one per prepend; version 0 is the shared Nil), not the 0+1+...+99 = 4950 that copying each
    version would cost. Keeping the full history costs one cell per change, which is what makes undo
    stacks, time-travel debugging and lock-free snapshots practical.
    */
    static void endToEnd() {
        System.out.println("[Section 5] 100 versions, none mutated");
        @SuppressWarnings("unchecked")
        FList<Integer>[] versions = (FList<Integer>[]) new FList<?>[100];

        versions[0] = FList.empty();
        for (int i = 1; i < versions.length; i++) versions[i] = versions[i - 1].prepend(i);

        // (1) every version still has the length it had at creation
        boolean allSizesOk = true;
        for (int i = 0; i < versions.length; i++) {
            if (versions[i].size() != i) { allSizesOk = false; break; }
        }

        // (2) the newest version's tail is (==) the version before it: shared, not copied
        boolean tailIsPrevByReference =
                ((Cons<Integer>) versions[99]).tail() == versions[98];

        // (3) versions are distinct objects
        boolean distinct = versions[10] != versions[11];

        System.out.println("  every version retained its expected size?  " + allSizesOk);
        System.out.println("  versions[99].tail() == versions[98]?       " + tailIsPrevByReference);
        System.out.println("  versions are distinct objects?             " + distinct);
        System.out.println("  total cons cells allocated for all 100 versions: 99 (not 4950)");
        System.out.println("  end-to-end self-check: "
                + (allSizesOk && tailIsPrevByReference && distinct ? "✓" : "✗"));
    }

    public static void main(String[] args) {
        persistentVsEphemeral();
        structuralSharing();
        treeDemo();
        costModel();
        endToEnd();
        System.out.println("Mod004ImmutableDataStructures finished");
    }
}
