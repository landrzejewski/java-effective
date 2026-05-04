package pl.training.concurrency;

import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.StructuredTaskScope;

// =================================================================================================
// Section 1: The ThreadLocal problem with virtual threads
// =================================================================================================

/*
## The ThreadLocal problem with virtual threads

- A `ThreadLocal<T>` gives every thread its own slot. With platform threads
this is fine — there are at most a few hundred threads.
- With virtual threads there can be millions of them, each carrying a copy of
every `ThreadLocal` value. Memory cost scales with the number of in-flight
requests.
- `InheritableThreadLocal` (the only built-in inheritance mechanism) only
inherits values once at thread creation; mutations after that are not
propagated. Use it cautiously.
- `ThreadLocal` is also *mutable*: any code holding a reference to the
ThreadLocal instance can rewrite the value. That makes audits hard.
- For *immutable, lexically-bound, request-scoped* context propagation,
`ScopedValue` (JEP 506, finalized in Java 25) is the right tool.
*/

// =================================================================================================
// Section 2: Declaring a ScopedValue
// =================================================================================================

/*
## Declaring a ScopedValue

- A `ScopedValue<T>` is a typed key, declared once per program (typically
`static final`):
```java
static final ScopedValue<String> CURRENT_USER = ScopedValue.newInstance();
```
- A binding is established with `ScopedValue.where(KEY, value).run(Runnable)`
or `.call(Callable)`. Inside the body, `KEY.get()` returns the value.
- Outside the binding, `KEY.get()` throws `NoSuchElementException`. Always
guard with `KEY.isBound()` if you might be called outside a bound region.
- The binding is **immutable for its lifetime** — there is no setter. If you
want a different value, open a new binding.
*/

// =================================================================================================
// Section 3: Reading from a deep call chain
// =================================================================================================

/*
## Reading from a deep call chain

- The whole point of `ScopedValue` is that the binding is visible to every
method called transitively from inside the `run()` body, without parameter
passing.
- A controller binds the request context, the service reads it, the repository
reads it. None of the methods need an extra argument.
- Compared to `ThreadLocal`, the read is just as cheap (final reference, no
hash lookup) and the code is much easier to audit because the only places
that *bind* are clearly visible at the call sites.
*/

// =================================================================================================
// Section 4: Rebinding in nested scopes
// =================================================================================================

/*
## Rebinding in nested scopes

- A nested `where(KEY, otherValue).run(...)` shadows the outer binding for the
duration of the inner block. When the inner block finishes, the outer value is
restored.
- This mirrors the lexical-scope behaviour of Rust shadowing — the variable
keeps the same name but takes a different value within an inner region.
- Typical use: an outer binding for the user, an inner binding that runs an
audit-log call as `system` and then returns to the user binding.
*/

// =================================================================================================
// Section 5: Inheritance into StructuredTaskScope
// =================================================================================================

/*
## Inheritance into StructuredTaskScope

- When a `ScopedValue` binding is active, every sub-task forked from a
`StructuredTaskScope` inside the binding **inherits** the value automatically.
- This is exactly the propagation pattern web frameworks need: bind the trace
id and the current user once at the top of the request handler, then let the
inner fan-out call services without thinking about how to forward the
context.
- The inheritance is structural and zero-allocation — the JVM stores the
binding in a single immutable list shared between parent and children.
- This is a *big* qualitative win over `InheritableThreadLocal`, which only
copies values once and not into virtual-thread sub-tasks created later via
structured concurrency.
*/

// =================================================================================================
// Section 6: When ScopedValue is not enough
// =================================================================================================

/*
## When ScopedValue is not enough

- `ScopedValue` is read-only. If you need *mutable* per-thread storage — say,
a reusable `StringBuilder` buffer or a per-request mutable counter that
methods incrementally update — `ThreadLocal` is still the right tool.
- For mutable per-request state with a small number of writers, an explicit
parameter or a context object (`Map<String,Object>` passed by reference) is
also fine and is often clearer.
- Bottom line: prefer `ScopedValue` for request context (immutable read-only
slots), keep `ThreadLocal` for caches and mutable thread-affinity stores.
*/

// =================================================================================================
// Section 7: Migration sketch
// =================================================================================================

/*
## Migration sketch

A typical `ThreadLocal` used for read-only context:

```java
// before
static final ThreadLocal<String> USER = new ThreadLocal<>();
void handle(Request r) {
    USER.set(r.user());
    try { service.process(); }
    finally { USER.remove(); }      // easy to forget
}
String currentUser() { return USER.get(); }

// after
static final ScopedValue<String> USER = ScopedValue.newInstance();
void handle(Request r) {
    ScopedValue.where(USER, r.user()).run(() -> service.process());
}
String currentUser() { return USER.get(); }   // throws if unbound
```

- The migrated version is exception-safe (no `try/finally`), allocation-free
(no `set` / `remove`), and immutable (no chance of leaking the user value into
a thread that the pool reuses for another request).
*/

public final class Mod013ScopedValues {

    private Mod013ScopedValues() {}

    // Three "request context" keys used across multiple sections.
    private static final ScopedValue<String> CURRENT_USER = ScopedValue.newInstance();
    private static final ScopedValue<String> TENANT_ID    = ScopedValue.newInstance();
    private static final ScopedValue<String> TRACE_ID     = ScopedValue.newInstance();

    // --- Section 1: the ThreadLocal-with-virtual-threads memory pressure ---
    static void threadLocalProblem() throws InterruptedException {
        System.out.println("[Section 1] ThreadLocal with virtual threads");

        var requestId = new ThreadLocal<String>();
        var seen = new ConcurrentSkipListSet<String>();
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 5; i++) {
                final int id = i;
                exec.execute(() -> {
                    requestId.set("req-" + id);
                    seen.add(requestId.get()); // each virtual thread sees its own value
                    // forgetting requestId.remove() leaks for as long as the VT lives
                });
            }
        }
        System.out.println("  ThreadLocal values seen across VTs = " + seen);
        System.out.println("  (works, but each VT carries the slot — see ScopedValue below)");
    }

    // --- Section 2: declaring + binding ---
    static void bindingBasics() {
        System.out.println("[Section 2] declaring + binding");

        // Outside the bound region: get() throws.
        try {
            CURRENT_USER.get();
        } catch (Exception e) {
            System.out.println("  unbound get() throws: " + e.getClass().getSimpleName());
        }

        ScopedValue.where(CURRENT_USER, "alice").run(() ->
                System.out.println("  inside binding: CURRENT_USER=" + CURRENT_USER.get()));

        System.out.println("  isBound() outside scope = " + CURRENT_USER.isBound());
    }

    // --- Section 3: deep call chain ---
    static void deepCallChain() {
        System.out.println("[Section 3] deep call chain — no parameter passing");

        ScopedValue.where(CURRENT_USER, "alice")
                .where(TENANT_ID, "acme")
                .run(Mod013ScopedValues::controller);
    }
    private static void controller() { service(); }
    private static void service() { repository(); }
    private static void repository() {
        System.out.println("  repo sees user=" + CURRENT_USER.get()
                + ", tenant=" + TENANT_ID.get());
    }

    // --- Section 4: rebinding ---
    static void rebinding() {
        System.out.println("[Section 4] rebinding in nested scopes");

        ScopedValue.where(CURRENT_USER, "alice").run(() -> {
            System.out.println("  outer: " + CURRENT_USER.get());
            ScopedValue.where(CURRENT_USER, "system").run(() ->
                    System.out.println("  inner (audit-log call): " + CURRENT_USER.get()));
            System.out.println("  outer restored: " + CURRENT_USER.get());
        });
    }

    // --- Section 5: inheritance into StructuredTaskScope sub-tasks ---
    static void inheritanceIntoScope() throws InterruptedException {
        System.out.println("[Section 5] inheritance into StructuredTaskScope");

        ScopedValue.where(TRACE_ID, "req-42").run(() -> {
            try (var scope = StructuredTaskScope.open()) {
                for (int i = 0; i < 3; i++) {
                    final int branch = i;
                    scope.fork(() -> {
                        // No setter, no parameter passing — the value is inherited.
                        System.out.println("  branch " + branch + " sees TRACE_ID=" + TRACE_ID.get()
                                + " on " + Thread.currentThread());
                        return null;
                    });
                }
                scope.join();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        });
    }

    // --- Section 6: ScopedValue is read-only ---
    static void readOnlyShowcase() {
        System.out.println("[Section 6] ScopedValue is read-only");

        ScopedValue.where(CURRENT_USER, "alice").run(() -> {
            // No setter. Cannot do CURRENT_USER.set(...).
            System.out.println("  read=" + CURRENT_USER.get()
                    + ", binding cannot be mutated mid-scope");
        });
    }

    // --- Section 7: migration sketch in code ---
    static void migrationSketch() {
        System.out.println("[Section 7] migration sketch");

        // Before: a ThreadLocal-backed context (illustration).
        var beforeUser = new ThreadLocal<String>();
        try {
            beforeUser.set("alice");
            System.out.println("  before: " + beforeUser.get());
        } finally {
            beforeUser.remove(); // easy to forget
        }

        // After: equivalent code with ScopedValue — no try/finally, no leak risk.
        ScopedValue.where(CURRENT_USER, "alice").run(() ->
                System.out.println("  after:  " + CURRENT_USER.get()));
    }

    public static void main(String[] args) throws InterruptedException {
        threadLocalProblem();
        bindingBasics();
        deepCallChain();
        rebinding();
        inheritanceIntoScope();
        readOnlyShowcase();
        migrationSketch();
        System.out.println("Mod013ScopedValues finished");
    }
}
