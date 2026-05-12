package pl.training.functionalprogramming;


import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class FunctionalExamples {

    // Pure functions

    public static int abs(int value) {
        return value < 0 ? -value : value;
    }

    // Currying

    public static int add(int value, int otherValue) {
        return value + otherValue;
    }

    public static Function<Integer, Integer> add(int value) {
        return otherValue -> value + otherValue;
    }

    // Recursion

    public static int factorial(int number) {
        return factorialLoop(number, 1);
    }

    private static int factorialLoop(int currentNumber, int accumulator) {
        if (currentNumber <= 0) {
            return accumulator;
        }
        return factorialLoop(currentNumber - 1, currentNumber * accumulator);
    }

    public static int fibonacci(int elementIndex) {
        return fibonacciLoop(elementIndex, 0, 1);
    }

    private static int fibonacciLoop(int elementsLeft, int current, int next) {
        if (elementsLeft == 0) {
            return current;
        }
        return fibonacciLoop(elementsLeft - 1, next, current + next);
    }

    // Higher-order functions

    public static String formatResult(int n, Function<Integer, Integer> f) {
        return "Result: " + f.apply(n);
    }

    // Polymorphic functions

    public static <E> int findFirst(E[] xs, Predicate<E> predicate) {
        return findFirstLoop(xs, predicate, 0);
    }

    private static <E> int findFirstLoop(E[] xs, Predicate<E> predicate, int index) {
        if (index == xs.length) {
            return -1;
        }

        if (predicate.test(xs[index])) {
            return index;
        }

        return findFirstLoop(xs, predicate, index + 1);
    }

    public static boolean isEven(int value) {
        return value % 2 == 0;
    }

    public static <A, B, C> Function<B, C> partial(A a, BiFunction<A, B, C> fn) {
        return b -> fn.apply(a, b);
    }

    public static <A, B, C> Function<A, Function<B, C>> curry(BiFunction<A, B, C> fn) {
        return a -> b -> fn.apply(a, b);
    }

    public static <A, B, C> BiFunction<A, B, C> uncurry(Function<A, Function<B, C>> fn) {
        return (a, b) -> fn.apply(a).apply(b);
    }

    public static <A, B, C> Function<A, C> compose(Function<B, C> f, Function<A, B> g) {
        return a -> f.apply(g.apply(a));
    }

    // Functional data structures

    public sealed interface List<A> permits Nil, Cons {

        @SafeVarargs
        static <A> List<A> of(A... xs) {
            List<A> result = new Nil<>();
            for (int i = xs.length - 1; i >= 0; i--) {
                result = new Cons<>(xs[i], result);
            }
            return result;
        }

        static <A> List<A> empty() {
            return new Nil<>();
        }
    }

    public static final class Nil<A> implements List<A> {

        @Override
        public String toString() {
            return "Nil";
        }
    }

    public static final class Cons<A> implements List<A> {

        private final A head;
        private final List<A> tail;

        public Cons(A head, List<A> tail) {
            this.head = head;
            this.tail = tail;
        }

        public A head() {
            return head;
        }

        public List<A> tail() {
            return tail;
        }

        @Override
        public String toString() {
            return "Cons(" + head + ", " + tail + ")";
        }
    }

    public static int sum(List<Integer> xs) {
        if (xs instanceof Nil<?>) {
            return 0;
        }

        Cons<Integer> cons = (Cons<Integer>) xs;
        return cons.head() + sum(cons.tail());
    }

    public static double product(List<Double> xs) {
        if (xs instanceof Nil<?>) {
            return 1.0;
        }

        Cons<Double> cons = (Cons<Double>) xs;
        return cons.head() * product(cons.tail());
    }

    public static <A> List<A> tail(List<A> xs) {
        if (xs instanceof Cons<A> cons) {
            return cons.tail();
        }
        return new Nil<>();
    }

    public static <A> List<A> setHead(List<A> xs, A x) {
        if (xs instanceof Cons<A> cons) {
            return new Cons<>(x, cons.tail());
        }
        return new Nil<>();
    }

    public static <A> List<A> prepend(List<A> xs, A x) {
        return new Cons<>(x, xs);
    }

    public static <A> List<A> append(List<A> xs1, List<A> xs2) {
        if (xs1 instanceof Nil<?>) {
            return xs2;
        }

        Cons<A> cons = (Cons<A>) xs1;
        return new Cons<>(cons.head(), append(cons.tail(), xs2));
    }

    public static <A> List<A> drop(List<A> xs, int n) {
        if (n <= 0) {
            return xs;
        }

        if (xs instanceof Cons<A> cons) {
            return drop(cons.tail(), n - 1);
        }

        return new Nil<>();
    }

    public static <A> List<A> dropWhile(List<A> xs, Predicate<A> predicate) {
        if (xs instanceof Cons<A> cons) {
            if (predicate.test(cons.head())) {
                return dropWhile(cons.tail(), predicate);
            }
        }

        return xs;
    }

    public static <A> List<A> init(List<A> xs) {
        if (xs instanceof Nil<?>) {
            return new Nil<>();
        }

        Cons<A> cons = (Cons<A>) xs;

        if (cons.tail() instanceof Nil<?>) {
            return new Nil<>();
        }

        return new Cons<>(cons.head(), init(cons.tail()));
    }

    public static <A, B> B foldRight(List<A> xs, B value, BiFunction<A, B, B> f) {
        if (xs instanceof Nil<?>) {
            return value;
        }

        Cons<A> cons = (Cons<A>) xs;
        return f.apply(cons.head(), foldRight(cons.tail(), value, f));
    }

    public static int sumFr(List<Integer> xs) {
        return foldRight(xs, 0, Integer::sum);
    }

    public static double productFr(List<Integer> xs) {
        return foldRight(xs, 1.0, (a, b) -> a * b);
    }

    public static int lengthFr(List<Integer> xs) {
        return foldRight(xs, 0, (a, len) -> len + 1);
    }

    public static <A> List<A> reverse(List<A> xs) {
        return foldLeft(xs, List.empty(), (acc, h) -> new Cons<>(h, acc));
    }

    public static <A, B> B foldLeft(List<A> xs, B value, BiFunction<B, A, B> f) {
        if (xs instanceof Nil<?>) {
            return value;
        }

        Cons<A> cons = (Cons<A>) xs;
        return foldLeft(cons.tail(), f.apply(value, cons.head()), f);
    }

    public static int sumFl(List<Integer> xs) {
        return foldLeft(xs, 0, Integer::sum);
    }

    public static double productFl(List<Integer> xs) {
        return foldLeft(xs, 1.0, (a, b) -> a * b);
    }

    public static int lengthFl(List<Integer> xs) {
        return foldLeft(xs, 0, (len, x) -> len + 1);
    }

    public static <A, B> List<B> map(List<A> xs, Function<A, B> f) {
        return foldRight(xs, List.empty(), (a, acc) -> new Cons<>(f.apply(a), acc));
    }

    public static <A> List<A> filter(List<A> xs, Predicate<A> f) {
        return foldRight(xs, List.empty(), (a, acc) -> f.test(a) ? new Cons<>(a, acc) : acc);
    }

    // Tree

    public sealed interface Tree<A> permits Leaf, Branch {
    }

    public static final class Leaf<A> implements Tree<A> {

        private final A value;

        public Leaf(A value) {
            this.value = value;
        }

        public A value() {
            return value;
        }
    }

    public static final class Branch<A> implements Tree<A> {

        private final Tree<A> left;
        private final Tree<A> right;

        public Branch(Tree<A> left, Tree<A> right) {
            this.left = left;
            this.right = right;
        }

        public Tree<A> left() {
            return left;
        }

        public Tree<A> right() {
            return right;
        }
    }

    public static <A> int numberOfNodes(Tree<A> tree) {
        if (tree instanceof Leaf<?>) {
            return 1;
        }

        Branch<A> branch = (Branch<A>) tree;
        return 1 + numberOfNodes(branch.left()) + numberOfNodes(branch.right());
    }

    public static <A> int maxDepth(Tree<A> tree) {
        if (tree instanceof Leaf<?>) {
            return 0;
        }

        Branch<A> branch = (Branch<A>) tree;
        return 1 + Math.max(maxDepth(branch.left()), maxDepth(branch.right()));
    }

    public static <A, B> Tree<B> map(Tree<A> tree, Function<A, B> f) {
        if (tree instanceof Leaf<A> leaf) {
            return new Leaf<>(f.apply(leaf.value()));
        }

        Branch<A> branch = (Branch<A>) tree;
        return new Branch<>(map(branch.left(), f), map(branch.right(), f));
    }

    // Option

    public sealed interface Option<A> permits Some, None {

        default <B> Option<B> map(Function<A, B> f) {
            if (this instanceof Some<A> some) {
                return new Some<>(f.apply(some.value()));
            }
            return new None<>();
        }

        default A getOrElse(Supplier<A> defaultValue) {
            if (this instanceof Some<A> some) {
                return some.value();
            }
            return defaultValue.get();
        }

        default <B> Option<B> flatMap(Function<A, Option<B>> f) {
            return map(f).getOrElse(None::new);
        }

        default Option<A> orElse(Supplier<Option<A>> ob) {
            if (this instanceof Some<A>) {
                return this;
            }
            return ob.get();
        }

        default Option<A> filter(Predicate<A> f) {
            return flatMap(a -> f.test(a) ? new Some<>(a) : new None<>());
        }
    }

    public static final class Some<A> implements Option<A> {

        private final A value;

        public Some(A value) {
            this.value = value;
        }

        public A value() {
            return value;
        }
    }

    public static final class None<A> implements Option<A> {
    }

    public static Option<LocalDateTime> getTimestamp() {
        return new None<>();
    }

    // Either

    public sealed interface Either<E, A> permits Left, Right {

        default <B> Either<E, B> map(Function<A, B> f) {
            if (this instanceof Right<E, A> right) {
                return new Right<>(f.apply(right.value()));
            }
            return (Either<E, B>) this;
        }

        default Either<E, A> orElse(Supplier<Either<E, A>> f) {
            if (this instanceof Left<E, A>) {
                return f.get();
            }
            return this;
        }
    }

    public static final class Left<E, A> implements Either<E, A> {

        private final E value;

        public Left(E value) {
            this.value = value;
        }

        public E value() {
            return value;
        }
    }

    public static final class Right<E, A> implements Either<E, A> {

        private final A value;

        public Right(A value) {
            this.value = value;
        }

        public A value() {
            return value;
        }
    }

    public static Either<String, Integer> safeDiv(int x, int y) {
        try {
            return new Right<>(x / y);
        } catch (Exception e) {
            return new Left<>("Division by zero");
        }
    }

    // Lazy evaluation

    public static <A> A lazyIf(boolean condition, Supplier<A> onTrue, Supplier<A> onFalse) {
        return condition ? onTrue.get() : onFalse.get();
    }

    // Functional state

    @FunctionalInterface
    public interface Rnd {
        Pair<Integer, Rnd> nextInt();
    }

    public static final class SimpleRandom implements Rnd {

        private final long seed;

        public SimpleRandom(long seed) {
            this.seed = seed;
        }

        @Override
        public Pair<Integer, Rnd> nextInt() {
            long newSeed = (seed * 0x5DEECE66DL + 0xBL) & 0xFFFFFFFFFFFFL;
            Rnd nextRng = new SimpleRandom(newSeed);
            int n = (int) (newSeed >>> 16);
            return new Pair<>(n, nextRng);
        }
    }

    public static Pair<Integer, Rnd> nonNegativeInt(Rnd rnd) {
        Pair<Integer, Rnd> pair = rnd.nextInt();
        int i1 = pair.first();
        return new Pair<>(i1 < 0 ? -(i1 + 1) : i1, pair.second());
    }

    public static Pair<Double, Rnd> doubleValue(Rnd rnd) {
        Pair<Integer, Rnd> pair = nonNegativeInt(rnd);
        return new Pair<>(pair.first() / (Integer.MAX_VALUE + 1.0), pair.second());
    }

    static void main() {
        write("enter temperatue:")
                .flatMap(unused -> read())
                .map(Double::parseDouble)
                .map(convertTemperature)
                .flatMap(FunctionalExamples::write)
                .run();

    }

    @FunctionalInterface
    public interface IO<A> {

        A run();

        default <B> IO<B> map(Function<A, B> f) {
            return () -> f.apply(run());
        }

        default <B> IO<B> flatMap(Function<A, IO<B>> f) {
            return () -> f.apply(run()).run();
        }

        default <B> IO<Pair<A, B>> combine(IO<B> io) {
            return () -> new Pair<>(run(), io.run());
        }
    }

    public static IO<String> read() {
        return () -> {
            try {
                byte[] bytes = System.in.readAllBytes();
                return new String(bytes).trim();
            } catch (Exception e) {
                return "";
            }
        };
    }

    public static IO<Void> write(String text) {
        return () -> {
            System.out.println(text);
            return null;
        };
    }

    public static double fahrenheitToCelsius(double value) {
        return (value - 32) * 5.0 / 9.0;
    }

    public static String toFixed(double value) {
        return String.format("%.2f", value);
    }

    public static final Function<Double, String> convertTemperature =
            compose(FunctionalExamples::toFixed, FunctionalExamples::fahrenheitToCelsius);

    public static String formatTemperatureResult(String temperature) {
        return "Temperature is equal " + temperature + "°";
    }

    public static final IO<Void> program = write("Enter temperature in degrees Fahrenheit: ")
            .flatMap(ignored -> read())
            .map(Double::parseDouble)
            .map(convertTemperature)
            .map(FunctionalExamples::formatTemperatureResult)
            .flatMap(FunctionalExamples::write);

    // Utility Pair

    public record Pair<A, B>(A first, B second) {
    }
}
