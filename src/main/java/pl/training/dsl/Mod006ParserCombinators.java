package pl.training.dsl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/*
An external DSL, built with combinators

- Every DSL so far was internal: written in Java, checked by javac, limited to what Java syntax allows. An
  external DSL has its own notation — "amount > 100 and status = 'PAID'", a cron line, a formula in a
  spreadsheet cell — which means someone has to turn text into structure.
- The two classical routes are a hand-written recursive-descent parser (verbose, and the grammar is implicit in
  the control flow) and a generator such as ANTLR or JavaCC (a separate grammar file, a build step, generated
  sources). Parser combinators are the third: a parser is an ordinary value, and the grammar is built by
  combining small parsers with the same operators every time.
- Because a parser is a value, each production is a named Java expression you can print, reuse and unit-test on
  its own, and the grammar lives next to the AST it produces. This is the technique behind parsec (Haskell),
  fastparse (Scala) and swift-parsing (Swift).
- Note how the pieces line up with earlier modules: Parser<T> is Mod005's combinator shape applied to text, and
  what it produces is Mod004's sealed AST, evaluated by exactly the same kind of exhaustive switch.

Parser<T> and Result<T>

- A parser is a function from the remaining input to a Result: either Success(value, rest), having consumed a
  prefix, or Failure(expected, rest). A PRIMITIVE failure hands the input back untouched; a COMPOSED one may
  report a rest past what its first component already consumed - chr('a').and(chr('b')) on "ax" fails with
  rest "x". Backtracking is therefore not a property of Failure; it comes from `or`, which always retries the
  alternative against the original input.
- Failure carries what was expected rather than a message, so error text can be assembled by the caller.
- Parser<T> extends Function<String, Result<T>> and adds the combinators as default methods, so every primitive
  parser below is written as a lambda.
*/

public final class Mod006ParserCombinators {

    private Mod006ParserCombinators() {}

    // =================================================================================================
    // Result and Parser
    // =================================================================================================

    public sealed interface Result<T> permits Success, Failure {}
    public record Success<T>(T value, String rest) implements Result<T> {}
    public record Failure<T>(String expected, String rest) implements Result<T> {}

    public record Pair<A, B>(A first, B second) {}

    @FunctionalInterface
    public interface Parser<T> extends Function<String, Result<T>> {

        /** Transform the value, leave the consumption alone. */
        default <U> Parser<U> map(Function<? super T, ? extends U> fn) {
            return in -> switch (apply(in)) {
                case Success<T>(T value, String rest) -> new Success<>(fn.apply(value), rest);
                case Failure<T>(String expected, String rest) -> new Failure<>(expected, rest);
            };
        }

        /** Sequence, where the next parser is chosen by the value the previous one produced. */
        default <U> Parser<U> flatMap(Function<? super T, Parser<U>> fn) {
            return in -> switch (apply(in)) {
                case Success<T>(T value, String rest) -> fn.apply(value).apply(rest);
                case Failure<T>(String expected, String rest) -> new Failure<>(expected, rest);
            };
        }

        /** Sequence, keeping both values. */
        default <U> Parser<Pair<T, U>> and(Parser<U> other) {
            return flatMap(first -> other.map(second -> new Pair<>(first, second)));
        }

        /** Sequence, keeping the left value (used to drop separators and closing brackets). */
        default <U> Parser<T> skip(Parser<U> other) {
            return flatMap(value -> other.map(ignored -> value));
        }

        /** Sequence, keeping the right value (used to drop prefixes and opening brackets). */
        default <U> Parser<U> then(Parser<U> other) {
            return flatMap(ignored -> other);
        }

        /*
        Ordered choice. The alternative is tried against the *original* input, so a failed attempt costs
        nothing — this is where the backtracking lives. Order therefore matters: put the longer or more
        specific alternative first, which is why factor() tries a function call before a bare variable.
        */
        default Parser<T> or(Parser<T> alternative) {
            return in -> apply(in) instanceof Success<T> success ? success : alternative.apply(in);
        }

        /** Zero or more, always succeeds. */
        default Parser<List<T>> many() {
            return in -> {
                var values = new ArrayList<T>();
                String rest = in;
                while (apply(rest) instanceof Success<T>(T value, String tail)) {
                    values.add(value);
                    rest = tail;
                }
                return new Success<>(List.copyOf(values), rest);
            };
        }

        /** One or more. */
        default Parser<List<T>> many1() {
            return and(many()).map(pair -> concat(pair.first(), pair.second()));
        }

        /** Succeed with a default value instead of failing; consumes nothing on the default branch. */
        default Parser<T> optional(T defaultValue) {
            return or(success(defaultValue));
        }

        /*
        Values separated by something, with the separators discarded. A trailing separator is not an error:
        `many` simply stops at the first failed "separator then value" attempt and leaves the separator in the
        remaining input, for the caller to reject or accept.
        */
        default Parser<List<T>> separatedBy(Parser<?> separator) {
            return and(separator.then(this).many())
                    .map(pair -> concat(pair.first(), pair.second()))
                    .or(success(List.of()));
        }

        /** Discard the whitespace that follows a token, so the grammar need not mention spaces. */
        default Parser<T> token() { return skip(whitespace()); }
    }

    private static <T> List<T> concat(T head, List<T> tail) {
        var all = new ArrayList<T>(tail.size() + 1);
        all.add(head);
        all.addAll(tail);
        return List.copyOf(all);
    }

    // =================================================================================================
    // Primitive parsers
    // =================================================================================================

    public static <T> Parser<T> success(T value) { return in -> new Success<>(value, in); }

    public static Parser<Character> satisfy(String expected, IntPredicate predicate) {
        return in -> !in.isEmpty() && predicate.test(in.charAt(0))
                ? new Success<>(in.charAt(0), in.substring(1))
                : new Failure<>(expected, in);
    }

    public static Parser<Character> chr(char c) { return satisfy("'" + c + "'", ch -> ch == c); }

    public static final Parser<Character> DIGIT  = satisfy("digit",  Character::isDigit);
    public static final Parser<Character> LETTER = satisfy("letter", Character::isLetter);

    /*
    Note that whitespace() is many() over a parser that consumes at least one character. That matters:
    many() over a parser which can succeed *without* consuming input never terminates, because the loop makes
    no progress and never fails. So whitespace() itself must never be wrapped in many() — it already is one.
    */
    public static Parser<String> whitespace() {
        return satisfy("whitespace", Character::isWhitespace).many().map(ignored -> "");
    }

    /** Defers construction of a parser, which is how a grammar refers to itself without infinite recursion. */
    public static <T> Parser<T> lazy(Supplier<Parser<T>> supplier) {
        return in -> supplier.get().apply(in);
    }

    /*
    Left-associative infix operators: one operand, then any number of (operator, operand) pairs, folded from
    the left. The operator parser produces the function that combines two operands, so one combinator covers
    every precedence level — a level is just chainl1(nextTighterLevel, operatorsOfThisLevel).
    */
    public static <T> Parser<T> chainl1(Parser<T> operand, Parser<BinaryOperator<T>> operator) {
        return operand.and(operator.and(operand).many()).map(parsed -> {
            T accumulated = parsed.first();
            for (var step : parsed.second()) accumulated = step.first().apply(accumulated, step.second());
            return accumulated;
        });
    }

    // =================================================================================================
    // The AST the grammar produces (Mod004's shape: sealed interface + records)
    // =================================================================================================

    public sealed interface Expr permits Num, Var, Bin, Call {}
    public record Num(double value) implements Expr {}
    public record Var(String name) implements Expr {}
    public record Bin(char operator, Expr left, Expr right) implements Expr {}
    public record Call(String function, List<Expr> arguments) implements Expr {}

    // =================================================================================================
    // The grammar
    //
    //   expr       = term (('+' | '-') term)*
    //   term       = factor (('*' | '/') factor)*
    //   factor     = number | call | variable | '(' expr ')'
    //   call       = identifier '(' (expr (',' expr)*)? ')'
    //   number     = digit+ ('.' digit+)?
    //   identifier = letter (letter | digit)*
    //
    // Each production below is one method returning a parser, so the Java reads in the same order as the
    // grammar above. Recursive productions go through lazy(...), which is the only concession to the fact
    // that a Java expression cannot refer to itself while it is being constructed.
    // =================================================================================================

    private static final Parser<Double> NUMBER = DIGIT.many1()
            .and(chr('.').then(DIGIT.many1()).map(decimals -> "." + text(decimals)).optional(""))
            .map(parsed -> Double.parseDouble(text(parsed.first()) + parsed.second()));

    private static final Parser<String> IDENTIFIER = LETTER.and(LETTER.or(DIGIT).many())
            .map(parsed -> parsed.first() + text(parsed.second()));

    private static Parser<Expr> expr() {
        return chainl1(lazy(Mod006ParserCombinators::term), operators('+', '-'));
    }

    private static Parser<Expr> term() {
        return chainl1(lazy(Mod006ParserCombinators::factor), operators('*', '/'));
    }

    private static Parser<Expr> factor() {
        // Order matters (see `or`): a call starts with an identifier too, so it must come before a variable.
        return NUMBER.token().<Expr>map(Num::new)
                .or(call())
                .or(variable())
                .or(parenthesised());
    }

    private static Parser<Expr> call() {
        return IDENTIFIER.token()
                .and(chr('(').token()
                        .then(lazy(Mod006ParserCombinators::expr).separatedBy(chr(',').token()))
                        .skip(chr(')').token()))
                .map(parsed -> new Call(parsed.first(), parsed.second()));
    }

    private static Parser<Expr> variable() {
        return IDENTIFIER.token().map(Var::new);
    }

    private static Parser<Expr> parenthesised() {
        return chr('(').token().then(lazy(Mod006ParserCombinators::expr)).skip(chr(')').token());
    }

    /** One parser accepting either operator character and yielding the AST node builder for it. */
    private static Parser<BinaryOperator<Expr>> operators(char first, char second) {
        return operator(first).or(operator(second));
    }

    private static Parser<BinaryOperator<Expr>> operator(char symbol) {
        return chr(symbol).token().map(ignored -> (left, right) -> new Bin(symbol, left, right));
    }

    /** Parses one expression, skipping any leading whitespace. */
    public static Result<Expr> parse(String input) {
        return whitespace().then(expr()).apply(input);
    }

    private static String text(List<Character> characters) {
        var out = new StringBuilder(characters.size());
        characters.forEach(out::append);
        return out.toString();
    }

    // =================================================================================================
    // The evaluator — a second pass over the AST, exactly as in Mod004
    // =================================================================================================

    public static double eval(Expr expr, Map<String, Double> environment) {
        return switch (expr) {
            case Num(double value) -> value;
            case Var(String name) -> {
                Double bound = environment.get(name);
                if (bound == null) throw new IllegalArgumentException("unbound variable: " + name);
                yield bound;
            }
            case Bin(char operator, Expr left, Expr right) -> {
                double a = eval(left, environment), b = eval(right, environment);
                yield switch (operator) {
                    case '+' -> a + b;
                    case '-' -> a - b;
                    case '*' -> a * b;
                    case '/' -> a / b;
                    default  -> throw new IllegalStateException("unknown operator: " + operator);
                };
            }
            case Call(String function, List<Expr> arguments) -> {
                var values = arguments.stream().mapToDouble(argument -> eval(argument, environment)).toArray();
                yield switch (function) {
                    case "max" -> { checkArity("max", values, 2); yield Math.max(values[0], values[1]); }
                    case "min" -> { checkArity("min", values, 2); yield Math.min(values[0], values[1]); }
                    case "abs" -> { checkArity("abs", values, 1); yield Math.abs(values[0]); }
                    default    -> throw new IllegalArgumentException("unknown function: " + function);
                };
            }
        };
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    /* A wrong argument count is a domain error like any other, not an ArrayIndexOutOfBoundsException. */
    private static void checkArity(String function, double[] values, int expected) {
        if (values.length != expected) {
            throw new IllegalArgumentException(
                    function + " expects " + expected + " argument(s), got " + values.length);
        }
    }

    /*
    Primitive parsers

    - satisfy(expected, predicate) is the only thing that ever looks at a character; chr, DIGIT and LETTER are
      all built from it. A parser reports what it wanted, so a failure is data rather than a message.
    - A primitive failure consumes nothing, and `or` retries its alternative on the ORIGINAL input, which is
      what makes backtracking automatic even after a composed parser has consumed a prefix.
    */
    static void primitives() {
        System.out.println("[Section 1] primitive parsers");
        System.out.println("  DIGIT(\"1abc\")   = " + DIGIT.apply("1abc"));
        System.out.println("  DIGIT(\"xyz\")    = " + DIGIT.apply("xyz"));
        System.out.println("  chr('(')(\"(1)\") = " + chr('(').apply("(1)"));
    }

    /*
    Combinators

      map / flatMap      transform, or choose the next parser from the value just produced
      and / skip / then  sequence, keeping both values, the left one, or the right one
      or                 ordered choice with backtracking
      many / many1       zero or more, one or more
      optional(value)    succeed with a default instead of failing
      separatedBy(sep)   a list with the separators dropped
      token()            discard trailing whitespace
      lazy(supplier)     defer construction, so a grammar can refer to itself
    */
    static void combinators() {
        System.out.println("[Section 2] combinators");
        System.out.println("  DIGIT.many1(\"1234abc\")            = " + DIGIT.many1().apply("1234abc"));
        System.out.println("  (a|b)(\"bx\")                       = " + chr('a').or(chr('b')).apply("bx"));
        System.out.println("  DIGIT.separatedBy(',')(\"1,2,3,xy\") = " + DIGIT.separatedBy(chr(',')).apply("1,2,3,xy"));
        System.out.println("  DIGIT.optional('0')(\"xy\")          = " + DIGIT.optional('0').apply("xy"));
    }

    /*
    Precedence out of one combinator

    - Precedence is expressed by layering: expr is chainl1 over term, term is chainl1 over factor. A tighter
      level is simply the operand of the looser one, so "1 + 2 * 3" binds the multiplication first without any
      precedence table anywhere.
    - Left associativity comes from the fold direction inside chainl1: "8 - 3 - 2" parses as (8 - 3) - 2.
    */
    static void precedence() {
        System.out.println("[Section 3] precedence and associativity");
        System.out.println("  1 + 2 * 3 -> " + parse("1 + 2 * 3"));
        System.out.println("  8 - 3 - 2 -> " + parse("8 - 3 - 2"));
    }

    /*
    Parsing and evaluating are separate passes

    - The grammar produces an Expr and stops. Meaning is added afterwards by eval, over an environment of
      variable bindings.
    - Keeping the two apart is what lets the same text be evaluated, pretty-printed, constant-folded or
      compiled to something else — the Mod004 argument, arrived at from the other direction.
    */
    static void parseThenEvaluate() {
        System.out.println("[Section 4] parse, then evaluate");
        var environment = Map.of("pi", Math.PI, "r", 4.0);
        if (parse("pi * r * r") instanceof Success<Expr>(Expr ast, String _)) {
            System.out.println("  ast   = " + ast);
            System.out.println("  value = " + eval(ast, environment));
        }
    }

    /*
    End-to-end, with checks

    Each input is parsed, checked for leftover text, evaluated, and compared against a value computed in plain
    Java. Leftover input counts as a failure: a parser that succeeds on a prefix has not accepted the line.
    */
    static void endToEnd() {
        System.out.println("[Section 5] end-to-end with reference values");

        record Probe(String input, double expected) {}

        var environment = Map.of("pi", Math.PI, "r", 4.0, "x", 7.0, "y", 3.0);
        var probes = List.of(
                new Probe("1 + 2 * 3",      7.0),
                new Probe("(1 + 2) * 3",    9.0),
                new Probe("8 - 3 - 2",      3.0),
                new Probe("pi * r * r",     Math.PI * 16),
                new Probe("max(x, y) - 1",  6.0),
                new Probe("abs(0 - 5) + 1", 6.0));

        boolean allOk = true;
        for (var probe : probes) {
            if (!(parse(probe.input()) instanceof Success<Expr>(Expr ast, String rest)) || !rest.isEmpty()) {
                System.out.printf(Locale.ROOT, "  %-16s did not parse completely%n", probe.input());
                allOk = false;
                continue;
            }
            double actual = eval(ast, environment);
            boolean ok = Math.abs(actual - probe.expected()) < 1e-9;
            allOk &= ok;
            System.out.printf(Locale.ROOT, "  %-16s -> %-10.4f expected %-10.4f %s%n",
                    probe.input(), actual, probe.expected(), ok ? "ok" : "MISMATCH");
        }

        // A parser that stops early has not accepted the input; the leftover text is the evidence.
        System.out.println("  \"1 + \" leaves -> " + parse("1 + "));
        System.out.println("  all probes match? " + allOk);
    }

    public static void main(String[] args) {
        primitives();
        combinators();
        precedence();
        parseThenEvaluate();
        endToEnd();
        System.out.println("Mod006ParserCombinators finished");
    }
}
