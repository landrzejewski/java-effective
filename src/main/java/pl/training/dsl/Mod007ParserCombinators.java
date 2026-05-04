package pl.training.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;

// =================================================================================================
// Section 1: What a parser combinator is
// =================================================================================================

/*
## What a parser combinator is

- A *parser combinator* is a function `String -> Result<T>` that either
consumes a prefix of the input and produces a value, or fails.
- Instead of writing a recursive-descent parser by hand or relying on a
parser generator (ANTLR, JavaCC), you build bigger parsers from smaller
ones with first-class combinators (`and`, `or`, `many`, `map`).
- Pay-off: parsers are values you can store, name, and unit-test
individually. The grammar lives in code, in the same file as the AST it
produces.
- This module is a port of the Parsec/Combine idea, applied to a tiny
calculator language with variables, function calls, and the usual
operator precedence.
*/

// =================================================================================================
// Section 2: Parser<T> and Result<T>
// =================================================================================================

/*
## Parser<T> and Result<T>

- `Result<T>` is a sealed interface with two cases:
  - `Success(value, remaining)` — the parser consumed some prefix and
    yielded `value`.
  - `Failure(expected, remaining)` — the parser refused; `expected`
    describes what it would have accepted.
- `Parser<T>` is `Function<String, Result<T>>` plus default helper methods
  for the combinators below. Making it a `@FunctionalInterface` lets you
  write atomic parsers as lambdas.
*/

// =================================================================================================
// Section 3: Atomic parsers
// =================================================================================================

/*
## Atomic parsers

Building blocks at the character / token level: `chr`, `str`, `digit`,
`letter`, `whitespace`. Each consumes at most one prefix of the input and
returns a `Success` or a `Failure`.
*/

// =================================================================================================
// Section 4: Combinators
// =================================================================================================

/*
## Combinators

- `and(other)`        — sequence; both must succeed.
- `or(alternative)`   — try this first, if it fails try the alternative.
- `map(fn)`           — transform the produced value.
- `flatMap(fn)`       — chain a parser whose shape depends on the previous
                        result.
- `many()`            — zero-or-more, returns `List<T>`.
- `many1()`           — one-or-more.
- `separatedBy(sep)`  — list separated by `sep`; trailing `sep` not
                        permitted.
- `between(open, close)` — wrap a parser in delimiters.
- `optional(default)` — backtracks on failure, returns the default value.
- `lazy(supplier)`    — defers parser construction; needed for recursive
                        grammars ("the right-hand side of `+` is itself an
                        expression").
*/

// =================================================================================================
// Section 5: Operator precedence (chainl1)
// =================================================================================================

/*
## Operator precedence (chainl1)

- `chainl1(operand, opParser)` parses one or more `operand`s separated by
parsed operators, folding the result *left-associatively*.
- Builds the standard precedence stack: factor `* /` before term `+ -`,
both above the atom level (numbers, variables, parenthesised
expressions).
- This is the trick every infix-operator parser uses; baking it into a
combinator makes the grammar fit on one screen.
*/

// =================================================================================================
// Section 6: AST + evaluator
// =================================================================================================

/*
## AST + evaluator

- The parser does not evaluate. It produces an immutable
`Expr` AST; `eval(Expr, env)` walks the tree.
- Splitting parsing from evaluation lets you serialise, optimise, or
re-target the same AST. In a real DSL the same AST might be evaluated
by a JIT, transformed into SQL, or pretty-printed.
*/

// =================================================================================================
// Section 7: End-to-end
// =================================================================================================

/*
## End-to-end

- Parse and evaluate a handful of expressions, including variables and
function calls. Compare each result against an independent reference
computation; print the AST and the value side by side.
*/

public final class Mod007ParserCombinators {

    private Mod007ParserCombinators() {}

    // =================================================================================================
    // Result + Parser
    // =================================================================================================

    public sealed interface Result<T> permits Success, Failure {}
    public record Success<T>(T value, String remainder) implements Result<T> {}
    public record Failure<T>(String expected, String remainder) implements Result<T> {}

    @FunctionalInterface
    public interface Parser<T> extends Function<String, Result<T>> {

        default <U> Parser<U> map(Function<? super T, ? extends U> fn) {
            return in -> switch (this.apply(in)) {
                case Success<T>(T v, String rest) -> new Success<>(fn.apply(v), rest);
                case Failure<T>(String e, String rest) -> new Failure<>(e, rest);
            };
        }

        default <U> Parser<U> flatMap(Function<? super T, Parser<U>> fn) {
            return in -> switch (this.apply(in)) {
                case Success<T>(T v, String rest) -> fn.apply(v).apply(rest);
                case Failure<T>(String e, String rest) -> new Failure<>(e, rest);
            };
        }

        default <U> Parser<Pair<T, U>> and(Parser<U> other) {
            return flatMap(a -> other.map(b -> new Pair<>(a, b)));
        }

        default <U> Parser<T> skip(Parser<U> other) {
            return flatMap(a -> other.map(ignored -> a));
        }

        default <U> Parser<U> then(Parser<U> other) {
            return flatMap(ignored -> other);
        }

        default Parser<T> or(Parser<T> alternative) {
            return in -> {
                Result<T> r = this.apply(in);
                return r instanceof Success<T> ? r : alternative.apply(in);
            };
        }

        default Parser<List<T>> many() {
            return in -> {
                var values = new ArrayList<T>();
                String rest = in;
                while (true) {
                    Result<T> r = this.apply(rest);
                    if (r instanceof Success<T>(T v, String tail)) { values.add(v); rest = tail; }
                    else break;
                }
                return new Success<>(List.copyOf(values), rest);
            };
        }

        default Parser<List<T>> many1() {
            return this.and(this.many()).map(p -> {
                var out = new ArrayList<T>(); out.add(p.first()); out.addAll(p.second());
                return List.copyOf(out);
            });
        }

        default Parser<List<T>> separatedBy(Parser<?> separator) {
            return this.and(separator.then(this).many())
                    .map(p -> { var out = new ArrayList<T>(); out.add(p.first()); out.addAll(p.second()); return List.copyOf(out); })
                    .or(success(List.of()));
        }

        default <O, C> Parser<T> between(Parser<O> open, Parser<C> close) {
            return open.then(this).skip(close);
        }

        default Parser<T> token() { return this.skip(Mod007ParserCombinators.whitespace()); }
    }

    public record Pair<A, B>(A first, B second) {}

    // =================================================================================================
    // Atomic parsers
    // =================================================================================================

    public static <T> Parser<T> success(T value) { return in -> new Success<>(value, in); }
    public static <T> Parser<T> failure(String expected) { return in -> new Failure<>(expected, in); }

    public static Parser<Character> chr(char c) {
        return in -> {
            if (!in.isEmpty() && in.charAt(0) == c) return new Success<>(c, in.substring(1));
            return new Failure<>(String.valueOf(c), in);
        };
    }

    public static Parser<String> str(String s) {
        return in -> in.startsWith(s) ? new Success<>(s, in.substring(s.length()))
                                      : new Failure<>(s, in);
    }

    public static Parser<Character> satisfy(String label, java.util.function.IntPredicate predicate) {
        return in -> {
            if (!in.isEmpty() && predicate.test(in.charAt(0)))
                return new Success<>(in.charAt(0), in.substring(1));
            return new Failure<>(label, in);
        };
    }

    public static final Parser<Character> digit  = satisfy("digit",  Character::isDigit);
    public static final Parser<Character> letter = satisfy("letter", Character::isLetter);

    public static Parser<String> whitespace() {
        return satisfy("whitespace", Character::isWhitespace).many().map(cs -> "");
    }

    public static <T> Parser<T> lazy(Supplier<Parser<T>> supplier) {
        return in -> supplier.get().apply(in);
    }

    /** chainl1: `operand` parses one operand; `op` parses an operator-fn. Folds left. */
    public static <T> Parser<T> chainl1(Parser<T> operand, Parser<BinaryOperator<T>> op) {
        return operand.and(op.and(operand).many()).map(initial -> {
            T acc = initial.first();
            for (var step : initial.second()) {
                acc = step.first().apply(acc, step.second());
            }
            return acc;
        });
    }

    // =================================================================================================
    // AST
    // =================================================================================================

    public sealed interface Expr permits Num, Var, Bin, Call {}
    public record Num(double value) implements Expr {}
    public record Var(String name) implements Expr {}
    public record Bin(String op, Expr left, Expr right) implements Expr {}
    public record Call(String fn, List<Expr> args) implements Expr {}

    // =================================================================================================
    // Calculator grammar
    // =================================================================================================
    //   expr     = term ('+' term | '-' term)*
    //   term     = factor ('*' factor | '/' factor)*
    //   factor   = number | variable | call | '(' expr ')'
    //   call     = identifier '(' arg-list ')'
    //   number   = digits ('.' digits)?
    //   variable = identifier
    //   identifier = letter (letter | digit)*

    private static final Parser<Expr> NUMBER = digit.many1().and(
            chr('.').then(digit.many1()).map(ds -> "." + asString(ds)).or(success("")))
            .map(p -> new Num(Double.parseDouble(asString(p.first()) + p.second())));

    private static final Parser<String> IDENT = letter.and(letter.or(digit).many())
            .map(p -> p.first() + asString(p.second()));

    private static Parser<Expr> expr;            // forward declared for recursion

    private static Parser<Expr> arg()   { return lazy(() -> expr).token(); }

    private static Parser<Expr> call() {
        return IDENT.token()
                .and(chr('(').token().then(arg().separatedBy(chr(',').token())).skip(chr(')').token()))
                .map(p -> new Call(p.first(), p.second()));
    }

    private static Parser<Expr> variable() {
        return IDENT.token().map(Var::new);
    }

    private static Parser<Expr> parenthesised() {
        return chr('(').token().then(lazy(() -> expr)).skip(chr(')').token());
    }

    private static Parser<Expr> factor() {
        // Order matters — try call before variable (call needs identifier + '(').
        return NUMBER.token()
                .or((Parser<Expr>) (Object) call())
                .or(variable())
                .or(parenthesised());
    }

    private static Parser<BinaryOperator<Expr>> mulDiv() {
        return chr('*').token().<BinaryOperator<Expr>>map(__ -> (a, b) -> new Bin("*", a, b))
                .or(chr('/').token().<BinaryOperator<Expr>>map(__ -> (a, b) -> new Bin("/", a, b)));
    }

    private static Parser<BinaryOperator<Expr>> plusMinus() {
        return chr('+').token().<BinaryOperator<Expr>>map(__ -> (a, b) -> new Bin("+", a, b))
                .or(chr('-').token().<BinaryOperator<Expr>>map(__ -> (a, b) -> new Bin("-", a, b)));
    }

    static {
        Parser<Expr> term = chainl1(factor(), mulDiv());
        expr = chainl1(term, plusMinus());
    }

    // =================================================================================================
    // Evaluator
    // =================================================================================================

    static double eval(Expr e, Map<String, Double> env) {
        return switch (e) {
            case Num(double v)               -> v;
            case Var(String name)            -> {
                Double v = env.get(name);
                if (v == null) throw new IllegalArgumentException("unbound variable: " + name);
                yield v;
            }
            case Bin(String op, Expr l, Expr r) -> {
                double a = eval(l, env), b = eval(r, env);
                yield switch (op) {
                    case "+" -> a + b;
                    case "-" -> a - b;
                    case "*" -> a * b;
                    case "/" -> a / b;
                    default  -> throw new IllegalStateException("unknown op " + op);
                };
            }
            case Call(String fn, List<Expr> args) -> {
                var values = args.stream().mapToDouble(a -> eval(a, env)).toArray();
                yield switch (fn) {
                    case "max" -> Math.max(values[0], values[1]);
                    case "min" -> Math.min(values[0], values[1]);
                    case "abs" -> Math.abs(values[0]);
                    case "sin" -> Math.sin(values[0]);
                    case "cos" -> Math.cos(values[0]);
                    default    -> throw new IllegalArgumentException("unknown function: " + fn);
                };
            }
        };
    }

    private static String asString(List<Character> chars) {
        var sb = new StringBuilder(chars.size());
        for (var c : chars) sb.append(c);
        return sb.toString();
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    static void atomicParsers() {
        System.out.println("[Section 3] atomic parsers");
        System.out.println("  digit('1abc')          = " + digit.apply("1abc"));
        System.out.println("  digit('xyz')           = " + digit.apply("xyz"));
        System.out.println("  str('let')('let x')    = " + str("let").apply("let x"));
    }

    static void combinatorsDemo() {
        System.out.println("[Section 4] combinators");
        Parser<List<Character>> digits = digit.many1();
        System.out.println("  digit.many1('1234abc') = " + digits.apply("1234abc"));

        Parser<Character> ab = chr('a').or(chr('b'));
        System.out.println("  a|b('ax') = " + ab.apply("ax"));
        System.out.println("  a|b('cx') = " + ab.apply("cx"));

        Parser<List<Character>> commaSeparated = digit.separatedBy(chr(','));
        System.out.println("  separatedBy = " + commaSeparated.apply("1,2,3,xy"));
    }

    static void operatorPrecedenceDemo() {
        System.out.println("[Section 5] operator precedence");
        Result<Expr> r = expr.apply("1 + 2 * 3");
        System.out.println("  AST of '1 + 2 * 3' = " + r);
        // Expected:  Bin(+, Num(1), Bin(*, Num(2), Num(3)))
    }

    static void astAndEval() {
        System.out.println("[Section 6] AST + evaluator");
        var env = Map.of("pi", Math.PI, "r", 4.0);
        Result<Expr> result = expr.apply("pi * r * r");
        if (result instanceof Success<Expr>(Expr ast, String _)) {
            System.out.println("  AST    = " + ast);
            System.out.println("  result = " + eval(ast, env));
        }
    }

    static void endToEnd() {
        System.out.println("[Section 7] end-to-end with reference assertions");
        var env = Map.of("pi", Math.PI, "r", 4.0, "x", 7.0, "y", 3.0);

        record Probe(String input, double expected) {}
        var probes = List.of(
                new Probe("1 + 2 * 3",        7.0),
                new Probe("(1 + 2) * 3",      9.0),
                new Probe("pi * r * r",       Math.PI * 16),
                new Probe("max(x, y) - 1",    6.0),
                new Probe("abs(0 - 5) + 1",   6.0));

        boolean allOk = true;
        for (var p : probes) {
            Result<Expr> r = expr.apply(p.input);
            if (!(r instanceof Success<Expr>(Expr ast, String tail)) || !tail.isBlank()) {
                System.out.printf("  '%s' did not parse cleanly (rest='%s') ✗%n", p.input, r);
                allOk = false; continue;
            }
            double got = eval(ast, env);
            boolean ok = Math.abs(got - p.expected) < 1e-9;
            if (!ok) allOk = false;
            System.out.printf("  '%-22s' → %-10.4f (expected %-10.4f) %s%n",
                    p.input, got, p.expected, ok ? "✓" : "✗");
        }
        System.out.println("  all probes match expected values? " + allOk);
    }

    public static void main(String[] args) {
        atomicParsers();
        combinatorsDemo();
        operatorPrecedenceDemo();
        astAndEval();
        endToEnd();
        System.out.println("Mod007ParserCombinators finished");
    }
}
