package pl.training.functionalfeatures;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Stream;

public final class Mod011Optional {

    private Mod011Optional() {}

    record Address(String city, String zip) {}
    record User(String id, String displayName, Address primaryAddress) {}

    private static final Map<String, User> USERS = Map.of(
            "u-1", new User("u-1", "alice", new Address("Krakow", "30-001")),
            "u-2", new User("u-2", "bob", null),                       // no address on file
            "u-3", new User("u-3", "carla", new Address("Gdansk", "80-001")));

    private static Optional<User> findUser(String id) {
        return Optional.ofNullable(USERS.get(id));
    }

    private static Optional<Address> findPrimaryAddress(User user) {
        return Optional.ofNullable(user.primaryAddress());
    }

    /*
    What Optional is for

    Optional<T> is a RETURN TYPE that makes "there may be no value here" part of the method signature. Its whole
    value is that the caller cannot ignore the empty case by accident - the compiler will not let them treat an
    Optional<User> as a User.

    Construction:
    - Optional.of(value)         - value must not be null; throws NullPointerException if it is. Use it when
                                   null would be a bug.
    - Optional.ofNullable(value) - null becomes empty. Use it at the boundary with an API that returns null.
    - Optional.empty()           - the absent value.

    Inspection:
    - isPresent() / isEmpty() (Java 11) - prefer isEmpty() over !isPresent(), it reads better in guards.

    Optional is NOT a general-purpose null replacement. It is designed for one job - a return value that may be
    missing - and Section 4 covers the places it does not belong.
    */
    static void construction() {
        System.out.println("[Section 1] construction");

        System.out.println("  found     = " + findUser("u-1"));
        System.out.println("  missing   = " + findUser("u-99"));
        System.out.println("  isEmpty() = " + findUser("u-99").isEmpty());

        try {
            Optional.of(USERS.get("u-99"));            // null -> NPE, on purpose
        } catch (NullPointerException e) {
            System.out.println("  Optional.of(null) threw NullPointerException - that is the point");
        }
    }

    /*
    The combinator API

    Everything below exists so that isPresent()/get() never has to appear in your code:

    - map(fn)                       - transform the value if present. Stays Optional<R>.
    - flatMap(fn)                   - for functions that THEMSELVES return an Optional. Without it you would get
                                      Optional<Optional<R>>.
    - filter(p)                     - keep the value only if it matches; otherwise empty.
    - or(supplier)                  - if empty, try another Optional. Chains alternatives (cache, then db).
    - ifPresent(c) / ifPresentOrElse(c, runnable) - the two-branch consumer form.
    - stream()                      - Optional<T> to a Stream<T> of size 0 or 1. See Section 5.

    map vs flatMap is the one thing to get right: if your function returns R, use map; if it returns
    Optional<R>, use flatMap. Getting it wrong is a compile error, not a bug - the types protect you.
    */
    static void combinators() {
        System.out.println("[Section 2] combinators");

        // map: User -> String, one level, stays Optional<String>.
        System.out.println("  map      = " + findUser("u-1").map(User::displayName).orElse("unknown"));

        // flatMap: findPrimaryAddress returns Optional<Address>. map would give Optional<Optional<Address>>.
        Optional<String> city = findUser("u-1")
                .flatMap(Mod011Optional::findPrimaryAddress)
                .map(Address::city);
        System.out.println("  flatMap  = " + city.orElse("<no address>"));

        // The same chain over a user WITHOUT an address: the empty propagates, nothing throws.
        System.out.println("  flatMap  = " + findUser("u-2")
                .flatMap(Mod011Optional::findPrimaryAddress)
                .map(Address::city)
                .orElse("<no address>"));

        // filter: narrow to empty when the predicate fails.
        System.out.println("  filter   = " + findUser("u-1")
                .filter(u -> u.displayName().startsWith("a"))
                .map(User::id).orElse("<filtered out>"));

        // or: fall back to another lookup. Both sides are Optional.
        System.out.println("  or       = " + findUser("u-99").or(() -> findUser("u-3"))
                .map(User::displayName).orElseThrow());

        // ifPresentOrElse: the two-branch form, replacing if (isPresent) ... else ...
        findUser("u-99").ifPresentOrElse(
                u -> System.out.println("  found " + u.displayName()),
                () -> System.out.println("  ifPresentOrElse -> ran the empty branch"));
    }

    /*
    Unwrapping: orElse vs orElseGet vs orElseThrow

    - orElse(value)          - the argument is an ordinary expression, so it is ALWAYS evaluated, even when the
                               Optional is present and the value is thrown away.
    - orElseGet(supplier)    - the supplier runs only when the Optional is empty.
    - orElseThrow()          - throws NoSuchElementException. The honest replacement for get().
    - orElseThrow(supplier)  - throws your own exception, built lazily.

    orElse with a cheap constant is fine and reads better. orElse with a method call is a bug waiting to happen:
    orElse(loadDefaultFromDatabase()) hits the database on every call, including the ones that did not need it.

    get() still exists and still works, but orElseThrow() says the same thing and names the failure. Treat get()
    as legacy.
    */
    static void unwrapping() {
        System.out.println("[Section 3] orElse vs orElseGet");

        var present = findUser("u-1");

        // orElse: the "default" is computed even though it is never used.
        String eager = present.map(User::displayName).orElse(expensiveDefault("orElse"));
        // orElseGet: the supplier is not invoked at all.
        String lazy = present.map(User::displayName).orElseGet(() -> expensiveDefault("orElseGet"));

        System.out.println("  orElse    -> " + eager);
        System.out.println("  orElseGet -> " + lazy + "   (notice only ONE 'computing' line above)");

        // orElseThrow: name the failure instead of returning a placeholder.
        try {
            findUser("u-99").orElseThrow();
        } catch (NoSuchElementException e) {
            System.out.println("  orElseThrow() -> " + e.getClass().getSimpleName());
        }
        try {
            findUser("u-99").orElseThrow(() -> new IllegalArgumentException("no such user: u-99"));
        } catch (IllegalArgumentException e) {
            System.out.println("  orElseThrow(supplier) -> " + e.getMessage());
        }
    }

    private static String expensiveDefault(String caller) {
        System.out.println("    (computing the default for " + caller + ")");
        return "anonymous";
    }

    /*
    Where Optional does not belong

    1. As a field. Optional is not Serializable, it costs an extra object per instance, and frameworks
       (Jackson, Hibernate, JPA) all have special cases for it. Keep the field nullable and return
       Optional.ofNullable(field) from the getter.
    2. As a method parameter. The caller now has three states to construct - value, empty, and null Optional -
       to express two. Overload the method or accept null instead.
    3. As Optional<List<T>> or Optional<Map<K,V>>. An empty collection already means "nothing found"; the
       wrapper adds a second way to say the same thing and forces every caller to unwrap before iterating.
    4. if (o.isPresent()) { use(o.get()); } - that is ifPresent(this::use), with no way to forget the check.

    The one place isPresent() earns its keep is a boolean expression that feeds something else, like a filter or
    an assertion.
    */
    static void antiPatterns() {
        System.out.println("[Section 4] anti-patterns");

        var u = findUser("u-1");

        // ANTI: isPresent + get
        if (u.isPresent()) {
            System.out.println("  anti:   " + u.get().displayName());
        }
        // PREFER:
        u.ifPresent(x -> System.out.println("  prefer: " + x.displayName()));

        // ANTI: Optional<List<T>>  ->  PREFER an empty list.
        System.out.println("  citiesIn('Nowhere') = " + citiesIn("Nowhere")
                + "   <- empty list, not Optional.empty()");
    }

    private static List<String> citiesIn(String prefix) {
        return USERS.values().stream()
                .map(User::primaryAddress)
                .flatMap(Stream::ofNullable)
                .map(Address::city)
                .filter(c -> c.startsWith(prefix))
                .toList();
    }

    /*
    Optional and streams

    The two types meet in three places:

    - Stream terminal operations return Optional: findFirst, findAny, min, max, reduce(BinaryOperator).
      That is why .orElseThrow() is so common at the end of a pipeline.
    - Optional.stream() turns an Optional into a 0-or-1 element stream. Combined with flatMap it filters out the
      empties AND unwraps the values in one step - replacing .filter(Optional::isPresent).map(Optional::get).
    - Stream.ofNullable(x) does the same job for a plain nullable value (used in citiesIn above).

    pl.training.functionalprogramming.Mod005OptionEitherTry builds this type from scratch and adds Either and
    Try, which carry a REASON for the absence - the natural next step when "empty" is not informative enough.
    */
    static void optionalAndStreams() {
        System.out.println("[Section 5] Optional meets Stream");

        // Terminal operations hand you an Optional.
        System.out.println("  max by name = " + USERS.values().stream()
                .max(java.util.Comparator.comparing(User::displayName))
                .map(User::displayName).orElseThrow());

        // flatMap(Optional::stream): drop the empties and unwrap, in one operation.
        var resolved = Stream.of("u-1", "u-99", "u-3")
                .map(Mod011Optional::findUser)
                .flatMap(Optional::stream)
                .map(User::displayName)
                .toList();
        System.out.println("  flatMap(Optional::stream) = " + resolved);

        // The verbose equivalent it replaces - same result, two operations and a get().
        var verbose = Stream.of("u-1", "u-99", "u-3")
                .map(Mod011Optional::findUser)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(User::displayName)
                .toList();
        System.out.println("  filter(isPresent).map(get) = " + verbose + "   (same, but with a get())");
    }

    public static void main(String[] args) {
        construction();
        combinators();
        unwrapping();
        antiPatterns();
        optionalAndStreams();
        System.out.println("Mod011Optional finished");
    }
}
