package pl.training.dsl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static pl.training.dsl.Mod004AstInterpreterDsl.AMOUNT;
import static pl.training.dsl.Mod004AstInterpreterDsl.CUSTOMER;
import static pl.training.dsl.Mod004AstInterpreterDsl.Cond;
import static pl.training.dsl.Mod004AstInterpreterDsl.ID;
import static pl.training.dsl.Mod004AstInterpreterDsl.Order;
import static pl.training.dsl.Mod004AstInterpreterDsl.ROWS;
import static pl.training.dsl.Mod004AstInterpreterDsl.STATUS;
import static pl.training.dsl.Mod004AstInterpreterDsl.like;
import static pl.training.dsl.Mod004AstInterpreterDsl.select;
import static pl.training.dsl.Mod004AstInterpreterDsl.toPredicate;
import static pl.training.dsl.Mod004AstInterpreterDsl.toSql;

class Mod004AstInterpreterDslTest {

    @Test
    @DisplayName("toSql emits placeholders, with the binds in the order the placeholders appear")
    void emitsParameterisedSql() {
        var sql = toSql(STATUS.eq("PAID").and(AMOUNT.gt(new BigDecimal("50"))).or(ID.lt(2)));

        assertAll(
                () -> assertEquals("((orders.status = ? AND orders.amount > ?) OR orders.id < ?)", sql.text()),
                () -> assertEquals(List.of("PAID", new BigDecimal("50"), 2), sql.binds()));
    }

    @Test
    @DisplayName("user data never reaches the SQL text")
    void userDataStaysInBinds() {
        var sql = toSql(CUSTOMER.eq("'; DROP TABLE orders; --"));

        assertAll(
                () -> assertEquals("orders.customer = ?", sql.text()),
                () -> assertEquals(List.of("'; DROP TABLE orders; --"), sql.binds()));
    }

    /*
    The property that matters for a code-as-data DSL: the two interpreters over one tree must agree. Each case
    below pairs a condition with an independently written Java predicate expressing the same intent.
    */
    static Stream<org.junit.jupiter.params.provider.Arguments> agreementCases() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("equality",
                        STATUS.eq("PAID"),
                        (Predicate<Order>) o -> o.status().equals("PAID")),
                org.junit.jupiter.params.provider.Arguments.of("or",
                        STATUS.eq("PAID").or(STATUS.eq("SHIPPED")),
                        (Predicate<Order>) o -> o.status().equals("PAID") || o.status().equals("SHIPPED")),
                org.junit.jupiter.params.provider.Arguments.of("and over two columns",
                        AMOUNT.gt(new BigDecimal("100")).and(like(CUSTOMER, "a%")),
                        (Predicate<Order>) o -> o.amount().compareTo(new BigDecimal("100")) > 0
                                && o.customer().startsWith("a")),
                org.junit.jupiter.params.provider.Arguments.of("numeric comparison",
                        ID.lt(3),
                        (Predicate<Order>) o -> o.id() < 3),
                org.junit.jupiter.params.provider.Arguments.of("like with a single-character wildcard",
                        like(CUSTOMER, "bo_"),
                        (Predicate<Order>) o -> o.customer().matches("bo.")),
                org.junit.jupiter.params.provider.Arguments.of("nothing matches",
                        STATUS.eq("UNKNOWN"),
                        (Predicate<Order>) o -> false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("agreementCases")
    @DisplayName("the in-memory interpreter agrees with a hand-written predicate")
    void interpretersAgree(String label, Cond cond, Predicate<Order> reference) {
        assertEquals(ROWS.stream().filter(reference).toList(),
                ROWS.stream().filter(toPredicate(cond)).toList());
    }

    @Test
    @DisplayName("the query chain projects the selected columns, in order")
    void projectsSelectedColumns() {
        var rows = select(CUSTOMER, STATUS).from("orders").where(STATUS.eq("PAID")).execute(ROWS);

        assertAll(
                () -> assertEquals(2, rows.size()),
                () -> assertEquals(List.of("customer", "status"), List.copyOf(rows.getFirst().keySet())),
                () -> assertEquals("alice", rows.getFirst().get("customer")));
    }

    @Test
    @DisplayName("a query with no condition keeps every row and emits no WHERE clause")
    void unconditionalQuery() {
        var query = select(ID).from("orders").all();

        assertAll(
                () -> assertEquals("SELECT orders.id FROM orders", query.toSql().text()),
                () -> assertEquals(List.of(), query.toSql().binds()),
                () -> assertEquals(ROWS.size(), query.execute(ROWS).size()));
    }

    @Test
    @DisplayName("a null column value is not a match, and does not throw")
    void nullValuesAreNotMatches() {
        var withNull = List.of(new Order(9, "dana", new BigDecimal("1.00"), "PAID"),
                new Order(10, null, new BigDecimal("2.00"), "PAID"));

        assertEquals(1, withNull.stream().filter(toPredicate(like(CUSTOMER, "d%"))).count());
    }
}
