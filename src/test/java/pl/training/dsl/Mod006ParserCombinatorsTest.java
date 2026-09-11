package pl.training.dsl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static pl.training.dsl.Mod006ParserCombinators.Bin;
import static pl.training.dsl.Mod006ParserCombinators.DIGIT;
import static pl.training.dsl.Mod006ParserCombinators.Expr;
import static pl.training.dsl.Mod006ParserCombinators.Failure;
import static pl.training.dsl.Mod006ParserCombinators.Num;
import static pl.training.dsl.Mod006ParserCombinators.Success;
import static pl.training.dsl.Mod006ParserCombinators.Var;
import static pl.training.dsl.Mod006ParserCombinators.chr;
import static pl.training.dsl.Mod006ParserCombinators.eval;
import static pl.training.dsl.Mod006ParserCombinators.parse;

class Mod006ParserCombinatorsTest {

    private static final Map<String, Double> ENV = Map.of("pi", Math.PI, "r", 4.0, "x", 7.0, "y", 3.0);

    /** Parses and insists the whole input was consumed — a prefix match is not an accepted expression. */
    private static Expr parseFully(String input) {
        var result = parse(input);
        var success = assertInstanceOf(Success.class, result, () -> "did not parse: " + input);
        assertEquals("", success.rest(), () -> "unconsumed input for: " + input);
        return (Expr) success.value();
    }

    @Test
    @DisplayName("a failing parser consumes nothing, which is what makes or() backtrack")
    void failureConsumesNothing() {
        var failure = assertInstanceOf(Failure.class, DIGIT.apply("xyz"));

        assertAll(
                () -> assertEquals("xyz", failure.rest()),
                () -> assertEquals("digit", failure.expected()),
                () -> assertEquals('b', ((Success<?>) chr('a').or(chr('b')).apply("bx")).value()));
    }

    @Test
    @DisplayName("* binds tighter than +, without any precedence table")
    void multiplicationBindsTighter() {
        assertEquals(new Bin('+', new Num(1), new Bin('*', new Num(2), new Num(3))), parseFully("1 + 2 * 3"));
    }

    @Test
    @DisplayName("chainl1 folds from the left, so subtraction is left-associative")
    void subtractionIsLeftAssociative() {
        assertEquals(new Bin('-', new Bin('-', new Num(8), new Num(3)), new Num(2)), parseFully("8 - 3 - 2"));
    }

    @Test
    @DisplayName("parentheses override precedence, and recursion goes through lazy()")
    void parenthesesRecurse() {
        assertEquals(new Bin('*', new Bin('+', new Num(1), new Num(2)), new Num(3)), parseFully("(1 + 2) * 3"));
    }

    @Test
    @DisplayName("a call is tried before a bare variable, because both start with an identifier")
    void callBeatsVariable() {
        var ast = parseFully("max(x, 1)");

        assertAll(
                () -> assertInstanceOf(Mod006ParserCombinators.Call.class, ast),
                () -> assertEquals(List.of(new Var("x"), new Num(1)),
                        ((Mod006ParserCombinators.Call) ast).arguments()),
                () -> assertInstanceOf(Var.class, parseFully("maximum")));
    }

    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
            "1 + 2 * 3,       7.0",
            "(1 + 2) * 3,     9.0",
            "8 - 3 - 2,       3.0",
            "10 / 4,          2.5",
            "3.5 + 1.25,      4.75",
            "'max(x, y)',     7.0",
            "'min(x, y)',      3.0",
            "abs(0 - 5) + 1,  6.0",
            "pi * r * r,      50.26548245743669"})
    @DisplayName("parsing then evaluating reproduces the arithmetic")
    void evaluates(String input, double expected) {
        // A value containing a comma is written between the default quote characters ('...').
        assertEquals(expected, eval(parseFully(input), ENV), 1e-9);
    }

    @Test
    @DisplayName("whitespace around and between tokens is skipped")
    void whitespaceIsIgnored() {
        assertEquals(parseFully("1+2*3"), parseFully("   1  +  2  *  3   "));
    }

    @Test
    @DisplayName("a partial parse leaves the unconsumed text as evidence")
    void partialParseIsVisible() {
        var success = assertInstanceOf(Success.class, parse("1 + "));

        assertAll(
                () -> assertEquals(new Num(1), success.value()),
                () -> assertEquals("+ ", success.rest()));
    }

    @Test
    @DisplayName("evaluation reports unknown names rather than guessing")
    void unknownNamesAreReported() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> eval(parseFully("nope"), ENV)),
                () -> assertThrows(IllegalArgumentException.class, () -> eval(parseFully("nope(1)"), ENV)));
    }
}
