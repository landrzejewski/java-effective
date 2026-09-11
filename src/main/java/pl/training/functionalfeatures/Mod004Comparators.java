package pl.training.functionalfeatures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

public final class Mod004Comparators {

    private Mod004Comparators() {}

    record Employee(String name, String department, int salary, Integer managerId) {}

    private static final List<Employee> STAFF = List.of(
            new Employee("Alice", "Engineering", 12_000, 7),
            new Employee("Bob",   "Sales",        9_000, null),
            new Employee("Carla", "Engineering", 15_000, 7),
            new Employee("Dan",   "Sales",        9_000, 3),
            new Employee("Eve",   "Engineering", 12_000, null));

    /*
    Comparator.comparing - a key extractor instead of a comparison

    The pre-Java-8 way to order objects was to write the comparison by hand:

        (a, b) -> Integer.compare(a.salary(), b.salary())

    That is three concepts in one expression: which field, how to compare it, and which direction. It is also
    the classic place for the "subtract" bug - (a, b) -> a.salary() - b.salary() overflows for large values and
    silently returns the wrong sign.

    Comparator.comparing(keyExtractor) inverts the problem: you say only WHICH key, and the library derives the
    comparison from the key's natural order. You cannot get the sign wrong and you cannot overflow.

    - comparing(keyFn)                - key must be Comparable.
    - comparing(keyFn, keyComparator) - key is not Comparable, or needs a custom order.
    - comparingInt / comparingLong / comparingDouble - same, but the key stays unboxed.
    */
    static void comparingBasics() {
        System.out.println("[Section 1] Comparator.comparing");

        // Hand-written: correct, but the intent is buried in the mechanics.
        Comparator<Employee> handWritten = (a, b) -> Integer.compare(a.salary(), b.salary());

        // Declarative: name the key, get the comparison for free.
        Comparator<Employee> bySalary = Comparator.comparingInt(Employee::salary);

        System.out.println("  hand-written first  = " + STAFF.stream().min(handWritten).orElseThrow().name());
        System.out.println("  comparingInt first  = " + STAFF.stream().min(bySalary).orElseThrow().name());

        // comparing(keyFn, keyComparator): order names case-insensitively.
        Comparator<Employee> byNameCi = Comparator.comparing(Employee::name, String.CASE_INSENSITIVE_ORDER);
        System.out.println("  case-insensitive    = " + sortedNames(byNameCi));
    }

    /*
    thenComparing - tie-breakers

    A single key rarely gives a total order. thenComparing appends a tie-breaker that is consulted only when the
    previous comparator returned 0:

        comparing(Employee::department)
            .thenComparing(Employee::salary)
            .thenComparing(Employee::name)

    Two overloads are easy to confuse:

    - thenComparing(keyExtractor)      - extract another key, compare it naturally.
    - thenComparing(otherComparator)   - chain a whole comparator.

    Both compile for a lambda, which is why an explicit method reference (Employee::name) is preferable: it makes
    the compiler pick the key-extractor overload unambiguously.

    thenComparingInt / thenComparingLong / thenComparingDouble avoid boxing the tie-breaker key.
    */
    static void thenComparingChains() {
        System.out.println("[Section 2] thenComparing");

        Comparator<Employee> byDeptThenSalaryThenName = Comparator
                .comparing(Employee::department)
                .thenComparingInt(Employee::salary)
                .thenComparing(Employee::name);

        STAFF.stream().sorted(byDeptThenSalaryThenName)
                .forEach(e -> System.out.printf(Locale.ROOT, "  %-12s %-12s %6d%n", e.department(), e.name(), e.salary()));
    }

    /*
    reversed() - and the trap it hides

    reversed() flips the comparator it is called on - which, in a chain, is the ENTIRE chain built so far, not
    just the last key:

        comparing(dept).thenComparing(salary).reversed()
            == reverse of (dept, then salary)          -- departments descending too

        comparing(dept).thenComparing(comparing(salary).reversed())
            == departments ascending, salaries descending

    This is the single most common Comparator bug. When only one key needs to flip, reverse THAT key's
    comparator, not the chain. Comparator.reverseOrder() and Collections.reverseOrder() give the same effect for
    a natural-order key.
    */
    static void reversedTrap() {
        System.out.println("[Section 3] reversed() reverses the whole chain");

        Comparator<Employee> wholeChainReversed = Comparator
                .comparing(Employee::department)
                .thenComparingInt(Employee::salary)
                .reversed();

        Comparator<Employee> onlySalaryReversed = Comparator
                .comparing(Employee::department)
                .thenComparing(Comparator.comparingInt(Employee::salary).reversed());

        System.out.println("  .reversed() on the chain  = " + sortedNames(wholeChainReversed));
        System.out.println("  reversed only the salary  = " + sortedNames(onlySalaryReversed));
        System.out.println("  ^ note the departments run in opposite directions");
    }

    /*
    Null-tolerant comparators

    A key extractor that can return null blows up with NullPointerException inside the sort - a confusing place
    to see one. Comparator.nullsFirst / nullsLast wrap another comparator and decide where nulls land:

        comparing(Employee::managerId, nullsLast(naturalOrder()))

    - The wrapped comparator never sees a null; it is only consulted when both values are non-null.
    - nullsFirst/nullsLast also work on the element itself: nullsFirst(comparing(Employee::name)) tolerates a
      null Employee in the list.

    Related building blocks: Comparator.naturalOrder(), Comparator.reverseOrder() - both are the identity-ish
    comparators you pass to the wrappers above.
    */
    static void nullHandling() {
        System.out.println("[Section 4] nullsFirst / nullsLast");

        // Employee::managerId is an Integer that may be null.
        Comparator<Employee> naive = Comparator.comparing(Employee::managerId);
        try {
            STAFF.stream().sorted(naive).toList();
        } catch (NullPointerException e) {
            System.out.println("  naive comparing(managerId) threw: " + e.getClass().getSimpleName());
        }

        Comparator<Employee> nullsLast =
                Comparator.comparing(Employee::managerId, Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<Employee> nullsFirst =
                Comparator.comparing(Employee::managerId, Comparator.nullsFirst(Comparator.naturalOrder()));

        System.out.println("  nullsLast  = " + STAFF.stream().sorted(nullsLast)
                .map(e -> e.name() + "/" + e.managerId()).toList());
        System.out.println("  nullsFirst = " + STAFF.stream().sorted(nullsFirst)
                .map(e -> e.name() + "/" + e.managerId()).toList());
    }

    /*
    Where a Comparator plugs in

    The same value works everywhere an order is needed - that is the payoff of treating the order as a value
    rather than as a sort algorithm:

    - stream.sorted(cmp), stream.min(cmp), stream.max(cmp)
    - list.sort(cmp) - in place, on a mutable list
    - Arrays.sort(array, cmp)
    - new TreeSet<>(cmp), new TreeMap<>(cmp) - the order becomes the container's identity
    - Collectors.maxBy(cmp) / minBy(cmp) as a groupingBy downstream (Mod008)
    - BinaryOperator.maxBy(cmp) / minBy(cmp) as a reduce or Map.merge function

    A comparator is also a functional interface, so a plain lambda still works wherever the combinators feel
    like overkill.
    */
    static void whereComparatorsPlugIn() {
        System.out.println("[Section 5] one comparator, many call sites");

        Comparator<Employee> bySalaryDesc = Comparator.comparingInt(Employee::salary).reversed();

        // 1. stream — min() of a descending comparator is the highest salary. Spelling it out because
        //    §3 was about exactly this kind of confusion.
        System.out.println("  stream.min(desc) = " + STAFF.stream().min(bySalaryDesc).orElseThrow().name()
                + "   (= the highest salary)");

        // 2. mutable list, sorted in place
        var mutable = new ArrayList<>(STAFF);
        mutable.sort(bySalaryDesc);
        System.out.println("  list.sort      = " + mutable.stream().map(Employee::name).toList());

        // 3. array
        Employee[] array = STAFF.toArray(Employee[]::new);
        Arrays.sort(array, bySalaryDesc);
        System.out.println("  Arrays.sort    = " + Arrays.stream(array).map(Employee::name).toList());

        // 4. the comparator BECOMES the container's ordering (and its notion of duplicates:
        //    the TreeSet below keeps one employee per distinct salary).
        var bySalarySet = new TreeSet<>(Comparator.comparingInt(Employee::salary));
        bySalarySet.addAll(STAFF);
        System.out.println("  TreeSet(cmp)   = " + bySalarySet.stream()
                .map(e -> e.name() + ":" + e.salary()).toList()
                + "  <- deduplicated by the comparator, not by equals");
    }

    private static List<String> sortedNames(Comparator<Employee> cmp) {
        return STAFF.stream().sorted(cmp).map(Employee::name).toList();
    }

    public static void main(String[] args) {
        comparingBasics();
        thenComparingChains();
        reversedTrap();
        nullHandling();
        whereComparatorsPlugIn();
        System.out.println("Mod004Comparators finished");
    }
}
