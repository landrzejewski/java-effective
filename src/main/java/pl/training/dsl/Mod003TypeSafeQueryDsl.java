package pl.training.dsl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

// =================================================================================================
// Section 1: Why typed query DSLs
// =================================================================================================

/*
## Why typed query DSLs

- String-concatenated SQL silently allows
  `WHERE customer_id = 'alice' AND amount = 'large'` even when both
  columns are typed (`int` vs. `string`). The driver finds out at runtime,
  the user gets an ugly stack trace.
- A typed DSL surfaces the type of each column as a Java generic and
  refuses to compile a comparison between two incompatible types.
- Pay-off scales with project size: the bigger the schema, the more value
  the DSL adds — typing once in the table definition replaces hundreds of
  hand-written queries that all need to stay in sync with the schema.
- This module is jOOQ-light: the same patterns, much smaller surface, no
  external dependencies.
*/

// =================================================================================================
// Section 2: Tables and columns as values
// =================================================================================================

/*
## Tables and columns as values

- A `Column<T>` carries the column's name, the table it belongs to, and a
phantom type `T` corresponding to its SQL value type.
- An `accessor` function maps a domain row (`Order`) to the column's value
— the same function powers in-memory execution (see §6) and could be
replaced by a JDBC `ResultSet` getter in a real driver.
- The table is a record holding its name and the list of columns. In a
real DSL the column list would be code-generated from a schema dump.
*/

// =================================================================================================
// Section 3: The fluent chain — restricted by stage
// =================================================================================================

/*
## The fluent chain — restricted by stage

- `select(c1, c2).from(table).where(predicate)` is implemented with three
distinct types: `SelectStage`, `FromStage`, `WhereStage`. Each stage's
public methods only allow what is legal next.
- `select` returns `SelectStage`; only `SelectStage.from(...)` exists, so
the chain has nowhere else to go.
- This is the "type-state" trick covered in depth in Mod008. Here it is
used lightly to enforce that `where` cannot precede `from` and `from`
cannot precede `select`.
*/

// =================================================================================================
// Section 4: Predicate composition
// =================================================================================================

/*
## Predicate composition

- A `Cond` is the Java mirror of a SQL boolean expression: each value
remembers its SQL fragment plus its bind parameters.
- Column-level helpers (`col.eq(v)`, `col.gt(v)`, `col.like(pattern)`) are
strongly typed — `col` of type `Column<String>` rejects `col.eq(42)` at
compile time.
- `Cond.and(other)` and `Cond.or(other)` combine atomic conditions and
keep the bind parameters in lock-step with the placeholders.
*/

// =================================================================================================
// Section 5: Producing the SQL
// =================================================================================================

/*
## Producing the SQL

- The query is a small AST: list of selected columns, source table,
optional `Cond`. `toSql()` walks the tree and emits a parameterised SQL
string + the list of binds.
- The DSL never concatenates user data into the SQL; binds are always
positional `?` markers.
- The reference SQL produced for the demo query is checked against an
expected string at runtime — a self-test.
*/

// =================================================================================================
// Section 6: Executing in-memory
// =================================================================================================

/*
## Executing in-memory

- `Query.execute(rows)` filters and projects an in-memory `List<Row>` the
same way a JDBC driver would. The accessor functions on each `Column<T>`
do the heavy lifting.
- Verifies that the DSL's *semantics* (what rows / columns the user
expects) match the SQL produced.
- A real DSL would also have a `.fetch(connection)` that runs against a
JDBC `Connection`. The interpretation step is otherwise identical.
*/

public final class Mod003TypeSafeQueryDsl {

    private Mod003TypeSafeQueryDsl() {}

    // =================================================================================================
    // Domain
    // =================================================================================================

    record Order(int id, String customer, BigDecimal amount, String status) {}

    private static final List<Order> DATA = List.of(
            new Order(1, "alice", new BigDecimal("119.50"), "PAID"),
            new Order(2, "bob",   new BigDecimal( "45.00"), "PENDING"),
            new Order(3, "alice", new BigDecimal("999.00"), "SHIPPED"),
            new Order(4, "carla", new BigDecimal( "12.50"), "PAID"),
            new Order(5, "bob",   new BigDecimal("210.00"), "CANCELLED"));

    // =================================================================================================
    // Column / Table — typed
    // =================================================================================================

    static final class Column<T> {
        final String name;
        final String tableName;
        final Function<Order, T> accessor;

        Column(String tableName, String name, Function<Order, T> accessor) {
            this.tableName = tableName; this.name = name; this.accessor = accessor;
        }

        public String qualified() { return tableName + "." + name; }

        // Comparators — all enforce the column's value type at compile time.
        public Cond eq(T value) { return atom(qualified() + " = ?", value); }
        public Cond gt(T value) { return atom(qualified() + " > ?", value); }
        public Cond lt(T value) { return atom(qualified() + " < ?", value); }
    }

    // String-only `like`; method exists only when the column is Column<String>.
    static Cond like(Column<String> column, String pattern) {
        return atom(column.qualified() + " LIKE ?", pattern);
    }

    static final class Table {
        final String name;
        final List<Column<?>> columns;
        Table(String name, Column<?>... columns) { this.name = name; this.columns = List.of(columns); }
        public String name() { return name; }
    }

    // ---- The Orders table ----
    static final Column<Integer>     ID       = new Column<>("orders", "id",       Order::id);
    static final Column<String>      CUSTOMER = new Column<>("orders", "customer", Order::customer);
    static final Column<BigDecimal>  AMOUNT   = new Column<>("orders", "amount",   Order::amount);
    static final Column<String>      STATUS   = new Column<>("orders", "status",   Order::status);
    static final Table ORDERS = new Table("orders", ID, CUSTOMER, AMOUNT, STATUS);

    // =================================================================================================
    // Cond — boolean expression node
    // =================================================================================================

    static final class Cond {
        final String sql;
        final List<Object> binds;
        final Predicate<Order> evaluator;

        Cond(String sql, List<Object> binds, Predicate<Order> evaluator) {
            this.sql = sql; this.binds = binds; this.evaluator = evaluator;
        }

        public Cond and(Cond other) {
            var combined = new ArrayList<>(this.binds); combined.addAll(other.binds);
            return new Cond("(" + this.sql + " AND " + other.sql + ")",
                    List.copyOf(combined), this.evaluator.and(other.evaluator));
        }
        public Cond or(Cond other) {
            var combined = new ArrayList<>(this.binds); combined.addAll(other.binds);
            return new Cond("(" + this.sql + " OR " + other.sql + ")",
                    List.copyOf(combined), this.evaluator.or(other.evaluator));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Cond atom(String sqlFragment, T value) {
        var binds = new ArrayList<Object>(); binds.add(value);

        // Build the row predicate by reading the column from the SQL fragment;
        // for the demo we accept that it parses something like "orders.amount > ?".
        String columnQualified = sqlFragment.substring(0, sqlFragment.indexOf(' '));
        String operator       = sqlFragment.substring(sqlFragment.indexOf(' ') + 1, sqlFragment.lastIndexOf(' '));
        Column<T> resolved = (Column<T>) resolveColumn(columnQualified);

        Predicate<Order> p = order -> {
            var actual = resolved.accessor.apply(order);
            return switch (operator) {
                case "="    -> java.util.Objects.equals(actual, value);
                case ">"    -> ((Comparable<Object>) actual).compareTo(value) > 0;
                case "<"    -> ((Comparable<Object>) actual).compareTo(value) < 0;
                case "LIKE" -> {
                    String pattern = ((String) value).replace("%", ".*").replace("_", ".");
                    yield ((String) actual).matches(pattern);
                }
                default -> throw new IllegalStateException("unknown op: " + operator);
            };
        };
        return new Cond(sqlFragment, List.copyOf(binds), p);
    }

    private static Column<?> resolveColumn(String qualified) {
        for (var c : ORDERS.columns) if (c.qualified().equals(qualified)) return c;
        throw new IllegalStateException("unknown column " + qualified);
    }

    // =================================================================================================
    // Query stages: SELECT → FROM → WHERE → toSql / execute
    // =================================================================================================

    static final class SelectStage {
        private final List<Column<?>> selected;
        private SelectStage(List<Column<?>> selected) { this.selected = selected; }
        public FromStage from(Table table) { return new FromStage(selected, table); }
    }

    static final class FromStage {
        private final List<Column<?>> selected;
        private final Table table;
        FromStage(List<Column<?>> selected, Table table) { this.selected = selected; this.table = table; }

        public Query where(Cond cond) { return new Query(selected, table, cond); }
        public Query toQuery()        { return new Query(selected, table, null); }
    }

    static SelectStage select(Column<?>... columns) {
        return new SelectStage(List.of(columns));
    }

    static final class Query {
        final List<Column<?>> selected;
        final Table table;
        final Cond cond;

        Query(List<Column<?>> selected, Table table, Cond cond) {
            this.selected = selected; this.table = table; this.cond = cond;
        }

        public SqlAndBinds toSql() {
            var sb = new StringBuilder("SELECT ");
            for (int i = 0; i < selected.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(selected.get(i).qualified());
            }
            sb.append(" FROM ").append(table.name);
            if (cond != null) sb.append(" WHERE ").append(cond.sql);
            return new SqlAndBinds(sb.toString(), cond == null ? List.of() : cond.binds);
        }

        public List<Map<String, Object>> execute(List<Order> rows) {
            var out = new ArrayList<Map<String, Object>>();
            for (var row : rows) {
                if (cond != null && !cond.evaluator.test(row)) continue;
                var projected = new LinkedHashMap<String, Object>();
                for (var c : selected) projected.put(c.name, c.accessor.apply(row));
                out.add(projected);
            }
            return out;
        }
    }

    record SqlAndBinds(String sql, List<Object> binds) {}

    // =================================================================================================
    // Sections wired into main
    // =================================================================================================

    // --- Section 2: tables and columns
    static void tablesAndColumns() {
        System.out.println("[Section 2] tables and columns");
        System.out.println("  ORDERS columns = " + ORDERS.columns.stream().map(Column::qualified).toList());
        System.out.println("  CUSTOMER type carrier = " + CUSTOMER.qualified() + " : Column<String>");
    }

    // --- Section 3: the fluent chain
    static void fluentChain() {
        System.out.println("[Section 3] fluent chain — types restrict the next step");

        var query = select(ID, CUSTOMER, AMOUNT)
                .from(ORDERS)
                .where(STATUS.eq("PAID").or(STATUS.eq("SHIPPED")));

        var sb = query.toSql();
        System.out.println("  SQL    = " + sb.sql());
        System.out.println("  binds  = " + sb.binds());

        // The lines below are intentionally NOT compiled — they are illegal in this DSL:
        //   select(ID).where(...);                 // FromStage missing
        //   select(ID).from(ORDERS).where(ID.eq("x"));  // wrong type for ID column
    }

    // --- Section 4: predicate composition with mixed types
    static void predicateComposition() {
        System.out.println("[Section 4] predicate composition");

        var cond = AMOUNT.gt(new BigDecimal("100"))
                .and(like(CUSTOMER, "a%"));         // String-only `like`
        System.out.println("  cond.sql   = " + cond.sql);
        System.out.println("  cond.binds = " + cond.binds);
    }

    // --- Section 5: producing SQL with a self-test against expected output
    static void producingSql() {
        System.out.println("[Section 5] producing SQL — self-test");

        var query = select(ID, AMOUNT)
                .from(ORDERS)
                .where(STATUS.eq("PAID").and(AMOUNT.gt(new BigDecimal("50"))));

        var sb = query.toSql();
        String expectedSql = "SELECT orders.id, orders.amount FROM orders " +
                "WHERE (orders.status = ? AND orders.amount > ?)";
        List<Object> expectedBinds = List.of("PAID", new BigDecimal("50"));

        System.out.println("  SQL = " + sb.sql());
        System.out.println("  expected matches actual? " + sb.sql().equals(expectedSql));
        System.out.println("  binds matches actual?    " + sb.binds().equals(expectedBinds));
    }

    // --- Section 6: in-memory execution
    static void inMemoryExecution() {
        System.out.println("[Section 6] in-memory execution against 5 rows");

        var query = select(CUSTOMER, AMOUNT, STATUS)
                .from(ORDERS)
                .where(STATUS.eq("PAID").or(STATUS.eq("SHIPPED")));

        System.out.println("  rows that match the DSL query:");
        query.execute(DATA).forEach(r -> System.out.println("    " + r));

        // Independent reference — what the same query *should* return.
        // Use LinkedHashMap so iteration order matches the DSL (column order).
        var reference = DATA.stream()
                .filter(o -> o.status().equals("PAID") || o.status().equals("SHIPPED"))
                .map(o -> {
                    var m = new LinkedHashMap<String, Object>();
                    m.put("customer", o.customer());
                    m.put("amount",   o.amount());
                    m.put("status",   o.status());
                    return m;
                })
                .toList();
        var dsl = query.execute(DATA);
        boolean equal = dsl.equals(reference);
        System.out.println("  DSL == reference? " + equal);
    }

    public static void main(String[] args) {
        tablesAndColumns();
        fluentChain();
        predicateComposition();
        producingSql();
        inMemoryExecution();
        System.out.println("Mod003TypeSafeQueryDsl finished");
    }
}
