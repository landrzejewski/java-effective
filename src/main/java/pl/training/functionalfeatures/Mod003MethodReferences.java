package pl.training.functionalfeatures;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

// =================================================================================================
// Section 1: Static method reference
// =================================================================================================

/*
## Static method reference

`ClassName::staticMethod` is sugar for `args -> ClassName.staticMethod(args)`.
- The compiler picks the SAM target (`Function<T,R>`, `Predicate<T>`, ...) by
  context.
- Method references are usually shorter and slightly more readable than the
  equivalent lambda. They also tend to compile to a single direct
  `invokestatic` indy bootstrap, while a lambda body wrapping a static call
  goes through one extra method.
- Common examples: `Integer::parseInt`, `Math::abs`, `String::valueOf`,
  `Objects::nonNull`.
*/

// =================================================================================================
// Section 2: Instance method reference on a type
// =================================================================================================

/*
## Instance method reference on a type

`ClassName::instanceMethod` (note: the type, not an object) means "call this
instance method *on the first argument*". The signature shifts:

- `String::toLowerCase`  ≡  `s -> s.toLowerCase()`         — `Function<String,String>`
- `String::length`       ≡  `s -> s.length()`              — `ToIntFunction<String>`
- `String::startsWith`   ≡  `(s, prefix) -> s.startsWith(prefix)` — `BiPredicate<String,String>`

The first parameter of the SAM becomes the receiver; the rest become the
method's actual arguments. This form is the most common in stream pipelines.
*/

// =================================================================================================
// Section 3: Instance method reference on a specific object
// =================================================================================================

/*
## Instance method reference on a specific object

`obj::instanceMethod` captures `obj` and calls the method on it:

- `logger::info`         ≡  `msg -> logger.info(msg)`
- `list::add`            ≡  `e -> list.add(e)`     — note the captured list mutates
- `System.out::println`  ≡  `x -> System.out.println(x)`

The captured object is evaluated **once** at the point the method reference is
created. Even if you reassign `obj` later, the method reference still points to
the original object.
*/

// =================================================================================================
// Section 4: Constructor reference
// =================================================================================================

/*
## Constructor reference

`ClassName::new` is a reference to a constructor.

- 0-arg form pairs with `Supplier<T>`: `ArrayList::new`.
- 1-arg form pairs with `Function<T,R>`: `Person::new` for
  `(String name) -> new Person(name)`.
- 2-arg form pairs with `BiFunction<T,U,R>`. For more arguments you need a
  custom functional interface.
- For arrays: `String[]::new` ≡ `n -> new String[n]`. Useful with
  `stream.toArray(String[]::new)`.
*/

// =================================================================================================
// Section 5: Composition
// =================================================================================================

/*
## Composition

Method references compose the same way lambdas do, since they are the same
type at runtime.

- `Function`: `f.andThen(g)`, `f.compose(g)`, `Function.identity()`.
- `Predicate`: `p.and(q)`, `p.or(q)`, `p.negate()`, `Predicate.not(p)`,
  `Predicate.isEqual(x)`.
- `Consumer`: `c1.andThen(c2)`.
- `Comparator`: `Comparator.comparing(keyFn)`, then `.thenComparing(...)`,
  `.reversed()`, `.thenComparingInt(toIntFn)`.

Composing predicates and functions out of named pieces lets you describe a
pipeline declaratively without writing a single block lambda.
*/

public final class Mod003MethodReferences {

    private Mod003MethodReferences() {}

    record Person(String name, int age) {
        Person(String name) { this(name, -1); } // 1-arg constructor for §4 example
    }

    private static final List<String> WORDS = List.of("Banana", "apple", "cherry", "Avocado", "");

    // --- Section 1: static method reference ---
    static void staticMethodRef() {
        System.out.println("[Section 1] static method reference");

        // String -> int via Integer.parseInt
        var nums = List.of("10", "200", "3", "42");
        int total = nums.stream().mapToInt(Integer::parseInt).sum();
        System.out.println("  total = " + total);

        // boolean: predicate built from Objects.nonNull
        Predicate<Object> notNull = java.util.Objects::nonNull;
        var filtered = Arrays.asList("a", null, "b", null, "c").stream().filter(notNull).toList();
        System.out.println("  non-null = " + filtered);
    }

    // --- Section 2: instance method on a type ---
    static void instanceOnType() {
        System.out.println("[Section 2] instance method on a TYPE");

        // String::toLowerCase  ≡  s -> s.toLowerCase()
        var lower = WORDS.stream().map(String::toLowerCase).toList();
        System.out.println("  lower = " + lower);

        // String::length       ≡  s -> s.length()
        int totalLength = WORDS.stream().mapToInt(String::length).sum();
        System.out.println("  total length = " + totalLength);

        // Sort by natural order via String::compareTo (used as a Comparator).
        var sorted = WORDS.stream().sorted(String::compareToIgnoreCase).toList();
        System.out.println("  sorted = " + sorted);
    }

    // --- Section 3: instance method on a specific object ---
    static void instanceOnObject() {
        System.out.println("[Section 3] instance method on an OBJECT");

        var sink = new ArrayList<String>();

        // sink::add captures the `sink` reference. Every invocation mutates that list.
        WORDS.stream().filter(Predicate.not(String::isBlank)).forEach(sink::add);
        System.out.println("  sink = " + sink);

        // System.out::println — same family.
        WORDS.stream().limit(2).forEach(System.out::println);
    }

    // --- Section 4: constructor references ---
    static void constructorRef() {
        System.out.println("[Section 4] constructor references");

        // 0-arg: Supplier -> new ArrayList<>()
        Supplier<List<String>> listFactory = ArrayList::new;
        var list = listFactory.get();
        list.add("x"); list.add("y");
        System.out.println("  ArrayList::new -> " + list);

        // 1-arg: Function<String, Person>
        Function<String, Person> namedPerson = Person::new; // matches Person(String)
        var alice = namedPerson.apply("Alice");
        System.out.println("  Person::new -> " + alice);

        // 2-arg: BiFunction<String, Integer, Person>
        BiFunction<String, Integer, Person> fullPerson = Person::new; // matches Person(String,int)
        var carla = fullPerson.apply("Carla", 35);
        System.out.println("  Person::new (2-arg) -> " + carla);

        // Array constructor reference: stream -> typed array.
        String[] arr = WORDS.stream().toArray(String[]::new);
        System.out.println("  String[]::new -> length=" + arr.length);
    }

    // --- Section 5: composition with method references ---
    static void composition() {
        System.out.println("[Section 5] composition");

        // Function pipeline: trim → lowercase → first 3 chars.
        Function<String, String> trim    = String::strip;
        Function<String, String> lower   = String::toLowerCase;
        Function<String, String> head3   = s -> s.substring(0, Math.min(3, s.length()));
        Function<String, String> norm    = trim.andThen(lower).andThen(head3);
        System.out.println("  norm.apply('   APPLE   ') = '" + norm.apply("   APPLE   ") + "'");

        // Predicate pipeline: non-blank AND starts with vowel.
        Predicate<String> notBlank = Predicate.not(String::isBlank);
        Predicate<String> startsWithVowel = s -> "aeiouAEIOU".indexOf(s.charAt(0)) >= 0;
        var vowels = WORDS.stream().filter(notBlank.and(startsWithVowel)).toList();
        System.out.println("  notBlank.and(vowel) -> " + vowels);

        // Comparator pipeline: by length descending, then natural order.
        var byShape = WORDS.stream()
                .filter(notBlank)
                .sorted(Comparator.comparingInt(String::length).reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .collect(Collectors.toList());
        System.out.println("  comparing.thenComparing -> " + byShape);
    }

    public static void main(String[] args) {
        staticMethodRef();
        instanceOnType();
        instanceOnObject();
        constructorRef();
        composition();
        System.out.println("Mod003MethodReferences finished");
    }
}
