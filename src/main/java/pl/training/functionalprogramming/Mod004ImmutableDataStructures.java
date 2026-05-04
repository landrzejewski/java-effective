package pl.training.functionalprogramming;

import java.util.function.Function;

// =================================================================================================
// Section 1: Persistent vs ephemeral
// =================================================================================================

/*
## Persistent vs ephemeral

- An *ephemeral* data structure is mutated in place — `ArrayList.add`,
`HashMap.put`. Old versions are gone after each modification.
- A *persistent* data structure preserves every previous version when an
"update" happens. The new version *shares* internal nodes with the old
one whenever possible — that is **structural sharing**.
- Persistence is what makes pure FP affordable: copy-on-write would be
prohibitive without sharing.
- This module shows the simplest persistent shapes (cons-cell list,
binary tree). Production-grade persistent collections (Vavr,
Eclipse Collections, Clojure's HAMT) use far cleverer structures, but
the core idea is the same.
*/

// =================================================================================================
// Section 2: Cons-cell List
// =================================================================================================

/*
## Cons-cell List

The classic linked list:

```
sealed interface List<A> permits Nil, Cons {}
record Nil<A>()                  implements List<A> {}
record Cons<A>(A head, List<A> tail) implements List<A> {}
```

- `prepend(x)` produces a new `Cons(x, this)` — O(1) and shares the tail.
- `append(x)` is O(n) — every `Cons` from the front must be re-built so
  the new tail can be appended. Persistent, but expensive on this shape.
- Folds, map, filter, reverse — all expressed via the same pattern match
  on `Nil` / `Cons`.
*/

// =================================================================================================
// Section 3: Structural sharing
// =================================================================================================

/*
## Structural sharing

Two lists `a` and `b = a.prepend(x)` share the suffix:

```
a:        1 → 2 → 3 → ∅
b:   x →  ─────────╯
```

`b.tail() == a` is *reference equality*. No copying happened. Modifying
either does not affect the other because the structure is immutable —
sharing is safe.

Inserting "into the middle" of a persistent list rebuilds the prefix in
front of the insertion point and shares the suffix from the insertion
point onward. The cost grows with the index, not with the total length
of the list.
*/

// =================================================================================================
// Section 4: Tree
// =================================================================================================

/*
## Tree

Persistent binary tree:

```
sealed interface Tree<A> permits Leaf, Branch {}
record Leaf<A>(A value)                   implements Tree<A> {}
record Branch<A>(Tree<A> left, Tree<A> right) implements Tree<A> {}
```

A `mapValues` walk re-uses the structure (same Branches with new Leaves)
when only values change. Inserting into a sorted tree returns a new
`Branch` along the path from the root to the insertion point; the
unchanged subtree is shared by reference.
*/

// =================================================================================================
// Section 5: Cost model
// =================================================================================================

/*
## Cost model (cons-list specifics)

| Operation       | Time    | Sharing                                    |
|-----------------|---------|--------------------------------------------|
| `prepend(x)`    | O(1)    | full tail shared                           |
| `head/tail`     | O(1)    | none needed                                |
| `append(x)`     | O(n)    | nothing — every front node is rebuilt      |
| `concat(other)` | O(n)    | the right operand is shared                |
| `map / filter`  | O(n)    | front rebuilt; tail values copied          |

For random-access patterns, use a `Vector` (e.g., Vavr's `Vector` based
on RRB-trees) instead. The cons-cell list above is for teaching, not for
production data piping.
*/

// =================================================================================================
// Section 6: End-to-end — 100 versions, none mutated
// =================================================================================================

/*
## End-to-end — 100 versions, none mutated

Build 100 persistent versions of a list by repeatedly prepending; keep
references to all of them in an array; verify that:

- every intermediate version still has the length it had at creation,
- the youngest version's tail (after one drop) has reference equality
  with version 99,
- versions are pairwise distinct objects with different sizes.

This is the pay-off of structural sharing: 100 versions live
simultaneously without paying 100× the memory.
*/

public final class Mod004ImmutableDataStructures {

    private Mod004ImmutableDataStructures() {}

    // =================================================================================================
    // List<A>
    // =================================================================================================

    public sealed interface FList<A> permits Nil, Cons {

        default boolean isEmpty()              { return this instanceof Nil<A>; }
        default int size()                     { return foldLeft(0, (acc, x) -> acc + 1); }
        default FList<A> prepend(A x)          { return new Cons<>(x, this); }
        default <B> B foldLeft(B zero, java.util.function.BiFunction<B, A, B> f) {
            B acc = zero; FList<A> here = this;
            while (here instanceof Cons<A>(A head, FList<A> tail)) {
                acc = f.apply(acc, head); here = tail;
            }
            return acc;
        }
        default <B> FList<B> map(Function<A, B> f) {
            return foldLeft(empty(), (FList<B> acc, A x) -> acc.prepend(f.apply(x))).reverse();
        }
        default FList<A> reverse() {
            return foldLeft(empty(), (FList<A> acc, A x) -> acc.prepend(x));
        }
        default java.util.List<A> toList() {
            var out = new java.util.ArrayList<A>(); FList<A> here = this;
            while (here instanceof Cons<A>(A head, FList<A> tail)) { out.add(head); here = tail; }
            return java.util.List.copyOf(out);
        }
        @SuppressWarnings("unchecked")
        static <A> FList<A> empty() { return (FList<A>) Nil.INSTANCE; }

        @SafeVarargs
        static <A> FList<A> of(A... xs) {
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
    // Tree<A>
    // =================================================================================================

    public sealed interface Tree<A> permits Leaf, Branch {
        default int size() {
            return switch (this) {
                case Leaf<A> _              -> 1;
                case Branch<A>(Tree<A> l, Tree<A> r) -> l.size() + r.size();
            };
        }
        default <B> Tree<B> mapValues(Function<A, B> f) {
            return switch (this) {
                case Leaf<A>(A v)            -> new Leaf<>(f.apply(v));
                case Branch<A>(Tree<A> l, Tree<A> r) -> new Branch<>(l.mapValues(f), r.mapValues(f));
            };
        }
    }
    public record Leaf<A>(A value)                   implements Tree<A> {}
    public record Branch<A>(Tree<A> left, Tree<A> right) implements Tree<A> {}

    // =================================================================================================
    // Sections
    // =================================================================================================

    static void persistentVsEphemeral() {
        System.out.println("[Section 1+2] cons-cell list");
        FList<Integer> a = FList.of(1, 2, 3);
        FList<Integer> b = a.prepend(0);
        System.out.println("  a       = " + a.toList());
        System.out.println("  b       = " + b.toList()  + " (same a, prepended 0)");
        System.out.println("  a.size  = " + a.size() + ", b.size = " + b.size());
    }

    static void structuralSharing() {
        System.out.println("[Section 3] structural sharing");
        FList<Integer> a = FList.of(1, 2, 3);
        FList<Integer> b = a.prepend(0);
        // b's tail is the same Object as a — sharing.
        Cons<Integer> bCons = (Cons<Integer>) b;
        boolean sharing = bCons.tail() == a;
        System.out.println("  b.tail() == a (reference equality)? " + sharing);
    }

    static void treeDemo() {
        System.out.println("[Section 4] tree");
        Tree<Integer> tree = new Branch<>(
                new Branch<>(new Leaf<>(1), new Leaf<>(2)),
                new Leaf<>(3));
        System.out.println("  size = " + tree.size());
        System.out.println("  mapValues(*10) sizes match? "
                + (tree.size() == tree.mapValues(x -> x * 10).size()));
    }

    static void costModel() {
        System.out.println("[Section 5] cost model — prepend O(1), append O(n)");
        FList<Integer> empty = FList.empty();
        long t1 = System.nanoTime();
        FList<Integer> built = empty;
        for (int i = 0; i < 100_000; i++) built = built.prepend(i);
        long prependMs = (System.nanoTime() - t1) / 1_000_000;
        System.out.println("  100k prepends took " + prependMs + " ms; size = " + built.size());
    }

    static void endToEnd() {
        System.out.println("[Section 6] 100 versions, none mutated");
        @SuppressWarnings("unchecked")
        FList<Integer>[] versions = (FList<Integer>[]) new FList<?>[100];

        versions[0] = FList.empty();
        for (int i = 1; i < versions.length; i++) versions[i] = versions[i - 1].prepend(i);

        // (1) every version still has the length it had at creation
        boolean allSizesOk = true;
        for (int i = 0; i < versions.length; i++) {
            if (versions[i].size() != i) { allSizesOk = false; break; }
        }

        // (2) latest version's tail equals (==) the previous version
        boolean tailIsPrevByReference =
                ((Cons<Integer>) versions[versions.length - 1]).tail() == versions[versions.length - 2];

        // (3) versions are distinct objects
        boolean distinct = versions[10] != versions[11];

        System.out.println("  every version retained its expected size?  " + allSizesOk);
        System.out.println("  v99.tail() == v98 (sharing)?               " + tailIsPrevByReference);
        System.out.println("  versions are distinct objects?            " + distinct);
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
