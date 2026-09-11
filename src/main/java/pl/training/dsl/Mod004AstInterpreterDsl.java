package pl.training.dsl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/*
Code as data — one tree, several interpreters

- The DSLs so far did their work as the caller typed: Mod002 appended to a StringBuilder, Mod003 filled fields.
  The alternative is for the DSL to build an immutable tree describing what was asked for, and to leave the
  doing to a separate pass over that tree.
- That indirection is what buys the interesting properties. The same tree can be printed, optimised, cached,
  serialised, sent over a wire, or executed by different back-ends. Here one condition tree is turned into
  parameterised SQL *and* into an in-memory Predicate, and §4 checks that the two agree.
- Sealed interfaces plus records plus record patterns make this cheap in modern Java: the tree is a handful of
  records, and each interpreter is one exhaustive switch. Add a node and every interpreter stops compiling
  until it handles it — the compiler maintains the invariant for you.
- This module is jOOQ-light: a typed column carries its value type, so comparing a text column to a number does
  not compile. Same idea, a fraction of the surface, no dependencies.
*/

public final class Mod004AstInterpreterDsl {

    private Mod004AstInterpreterDsl() {}

    // =================================================================================================
    // Domain
    // =================================================================================================

    record Order(int id, String customer, BigDecimal amount, String status) {}

    static final List<Order> ROWS = List.of(
            new Order(1, "alice", new BigDecimal("119.50"), "PAID"),
            new Order(2, "bob",   new BigDecimal("45.00"),  "PENDING"),
            new Order(3, "alice", new BigDecimal("999.00"), "SHIPPED"),
            new Order(4, "carla", new BigDecimal("12.50"),  "PAID"),
            new Order(5, "bob",   new BigDecimal("210.00"), "CANCELLED"));

    // =================================================================================================
    // Columns — the typed vocabulary of the DSL
    // =================================================================================================

    /*
    A Column<T> pairs the SQL column name with the Java type of its values and with an accessor that reads that
    value out of a row. T is a real type argument, not a phantom one (contrast Mod003): it appears in eq(T) and
    in the accessor, and that is exactly why STATUS.eq(42) does not compile.

    For brevity the row type is fixed to Order. A production DSL parameterises it too — Column<R, T> with a
    Function<R, T> — so that a column cannot be used against the wrong table; nothing else about the design
    changes.
    */
    record Column<T>(String table, String name, Function<Order, T> accessor) {

        String qualified() { return table + "." + name; }

        @Override public String toString() { return qualified(); }

        public Cond eq(T value) { return new Compare<>(this, Op.EQ, value); }
        public Cond gt(T value) { return new Compare<>(this, Op.GT, value); }
        public Cond lt(T value) { return new Compare<>(this, Op.LT, value); }
    }

    /*
    `like` only makes sense for text, so it must accept a Column<String> and nothing else. It cannot be an
    instance method: Java has no way to declare a method that exists only for one parameterisation of its own
    class (no `where T = String` clause), and no extension methods to add one from outside. A free static
    function whose parameter type carries the constraint is the idiomatic substitute — the same reason Mod003's
    send() had to be a static function.
    */
    static Cond like(Column<String> column, String pattern) {
        return new Compare<>(column, Op.LIKE, pattern);
    }

    static final Column<Integer>    ID       = new Column<>("orders", "id",       Order::id);
    static final Column<String>     CUSTOMER = new Column<>("orders", "customer", Order::customer);
    static final Column<BigDecimal> AMOUNT   = new Column<>("orders", "amount",   Order::amount);
    static final Column<String>     STATUS   = new Column<>("orders", "status",   Order::status);

    static final List<Column<?>> ORDER_COLUMNS = List.of(ID, CUSTOMER, AMOUNT, STATUS);

    // =================================================================================================
    // The AST — a condition is a value, not a side effect
    // =================================================================================================

    enum Op {
        EQ("="), GT(">"), LT("<"), LIKE("LIKE");
        final String sql;
        Op(String sql) { this.sql = sql; }
    }

    sealed interface Cond permits Compare, And, Or {
        default Cond and(Cond other) { return new And(this, other); }
        default Cond or (Cond other) { return new Or (this, other); }
    }

    /*
    The toString of each node prints the tree's shape rather than the SQL it would become — the point being
    that at this stage it is structure, and SQL is only one of the things that structure can turn into.
    */
    record Compare<T>(Column<T> column, Op op, T value) implements Cond {
        @Override public String toString() { return "Compare(" + column + " " + op + " " + value + ")"; }
    }
    record And(Cond left, Cond right) implements Cond {
        @Override public String toString() { return "And(" + left + ", " + right + ")"; }
    }
    record Or (Cond left, Cond right) implements Cond {
        @Override public String toString() { return "Or(" + left + ", " + right + ")"; }
    }

    // =================================================================================================
    // Interpreter 1 — emit parameterised SQL
    // =================================================================================================

    record Sql(String text, List<Object> binds) {}

    static Sql toSql(Cond cond) {
        var binds = new ArrayList<Object>();
        return new Sql(writeSql(cond, binds), List.copyOf(binds));
    }

    /** Walks the tree left to right, so the binds come out in the same order as the ? placeholders. */
    private static String writeSql(Cond cond, List<Object> binds) {
        return switch (cond) {
            case Compare<?>(Column<?> column, Op op, Object value) -> {
                binds.add(value);
                yield column.qualified() + " " + op.sql + " ?";
            }
            case And(Cond left, Cond right) -> "(" + writeSql(left, binds) + " AND " + writeSql(right, binds) + ")";
            case Or (Cond left, Cond right) -> "(" + writeSql(left, binds) + " OR "  + writeSql(right, binds) + ")";
        };
    }

    // =================================================================================================
    // Interpreter 2 — evaluate the same tree against in-memory rows
    // =================================================================================================

    static Predicate<Order> toPredicate(Cond cond) {
        return switch (cond) {
            case Compare<?> compare         -> compareRow(compare);
            case And(Cond left, Cond right) -> toPredicate(left).and(toPredicate(right));
            case Or (Cond left, Cond right) -> toPredicate(left).or (toPredicate(right));
        };
    }

    private static <T> Predicate<Order> compareRow(Compare<T> compare) {
        Function<Order, T> read = compare.column().accessor();
        T expected = compare.value();
        return row -> {
            T actual = read.apply(row);
            if (actual == null) return false;
            return switch (compare.op()) {
                // compareValues, not Objects.equals: BigDecimal.equals compares SCALE as well as value, so
                // AMOUNT.eq(new BigDecimal("119.5")) would miss a stored 119.50 in memory while matching it
                // in SQL - and the two back-ends agreeing is the whole point of this module.
                case EQ   -> compareValues(actual, expected) == 0;
                case GT   -> compareValues(actual, expected) > 0;
                case LT   -> compareValues(actual, expected) < 0;
                // A SQL LIKE pattern uses % for "any run" and _ for "any one character". Everything else is
                // literal, so it has to be quoted out of the regex - otherwise '.', '*' or '[' would keep
                // their regex meaning here and lose it in SQL.
                case LIKE -> ((String) actual).matches(likeToRegex((String) expected));
            };
        };
    }

    /** Translate a SQL LIKE pattern into a regex, quoting every character that is not a wildcard. */
    private static String likeToRegex(String pattern) {
        var regex = new StringBuilder();
        var literal = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            if (c == '%' || c == '_') {
                if (!literal.isEmpty()) {
                    regex.append(java.util.regex.Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                regex.append(c == '%' ? ".*" : ".");
            } else {
                literal.append(c);
            }
        }
        if (!literal.isEmpty()) {
            regex.append(java.util.regex.Pattern.quote(literal.toString()));
        }
        return regex.toString();
    }

    @SuppressWarnings("unchecked")   // Column<T> only ever holds Comparable values in this DSL
    private static <T> int compareValues(T a, T b) {
        return ((Comparable<T>) a).compareTo(b);
    }

    // =================================================================================================
    // The query chain — light type-state around the AST
    // =================================================================================================

    /*
    select(...) returns a stage whose only method is from(...), whose only method is where(...). The chain
    therefore has nowhere illegal to go, without any phantom parameters: this is the staged-interface encoding
    of Mod003 §4, applied to three steps.
    */
    record SelectStage(List<Column<?>> columns) {
        FromStage from(String table) { return new FromStage(columns, table); }
    }

    record FromStage(List<Column<?>> columns, String table) {
        Query where(Cond cond) { return new Query(columns, table, cond); }
        Query all()            { return new Query(columns, table, null); }
    }

    static SelectStage select(Column<?>... columns) { return new SelectStage(List.of(columns)); }

    record Query(List<Column<?>> columns, String table, Cond where) {

        Sql toSql() {
            var text = new StringBuilder("SELECT ");
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) text.append(", ");
                text.append(columns.get(i).qualified());
            }
            text.append(" FROM ").append(table);
            if (where == null) return new Sql(text.toString(), List.of());
            var sql = Mod004AstInterpreterDsl.toSql(where);
            return new Sql(text + " WHERE " + sql.text(), sql.binds());
        }

        /** The other back-end: run the very same tree over a list of rows. */
        List<Map<String, Object>> execute(List<Order> rows) {
            Predicate<Order> matches = where == null ? row -> true : toPredicate(where);
            var out = new ArrayList<Map<String, Object>>();
            for (var row : rows) {
                if (!matches.test(row)) continue;
                var projected = new LinkedHashMap<String, Object>();
                for (var column : columns) projected.put(column.name(), column.accessor().apply(row));
                out.add(projected);
            }
            return out;
        }
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    /*
    Typed columns

    - Each column is a value carrying its name and its Java value type. The comparison methods take T, so the
      column's type propagates into the call site and the compiler checks the literal you wrote against it.
    - The mistakes this rules out are the ones a string-built query only discovers when the driver rejects it:
        ID.eq("PAID")        int column compared to text
        STATUS.gt(42)        text column compared to a number
        like(AMOUNT, "1%")   LIKE on a numeric column
    */
    static void typedColumns() {
        System.out.println("[Section 1] typed columns");

        ORDER_COLUMNS.forEach(column -> System.out.println("  " + column.qualified()));
        System.out.println("  STATUS.eq(\"PAID\") builds: " + STATUS.eq("PAID"));
        System.out.println("  ID.eq(\"PAID\")     // rejected: String is not Integer");
    }

    /*
    The condition is a tree

    - eq / gt / lt / like build leaf nodes; and / or build interior nodes. Nothing is executed and no string is
      produced — the result is a value describing the request.
    - Because the nodes are records, they print, compare and pattern-match for free, and because Cond is sealed,
      any switch over it can be exhaustive without a default branch.
    */
    static void conditionIsATree() {
        System.out.println("[Section 2] the condition is a tree");

        Cond cond = STATUS.eq("PAID").or(STATUS.eq("SHIPPED")).and(AMOUNT.gt(new BigDecimal("50")));
        System.out.println("  " + cond);
        System.out.println("  root node type = " + cond.getClass().getSimpleName());
    }

    /*
    Two interpreters over one tree

    - toSql walks the tree and emits a parameterised string plus the binds, in placeholder order. User data
      never reaches the SQL text, so there is no injection surface — that is a structural property here, not a
      rule someone has to follow.
    - toPredicate walks the same tree and composes a Predicate<Order> from each column's accessor.
    - Adding a third back-end (a Mongo filter document, a pretty-printer, an optimiser that folds constants) is
      a new switch over the same nodes and changes nothing that already exists.
    */
    static void twoInterpreters() {
        System.out.println("[Section 3] two interpreters, one tree");

        Cond cond = AMOUNT.gt(new BigDecimal("100")).and(like(CUSTOMER, "a%"));

        var sql = toSql(cond);
        System.out.println("  toSql().text  = " + sql.text());
        System.out.println("  toSql().binds = " + sql.binds());
        System.out.println("  toPredicate() keeps = "
                + ROWS.stream().filter(toPredicate(cond)).map(Order::customer).toList());
    }

    /*
    The chain, and the two back-ends agreeing

    - select(...).from(...).where(...) assembles the query; toSql() and execute(rows) are the two ways to cash
      it in.
    - The check below is the point of the module: the rows kept by the in-memory interpreter are compared with
      an independent hand-written stream, and the emitted SQL with a hand-written string. If the two
      interpreters ever drift apart, this section says so.
    */
    static void endToEnd() {
        System.out.println("[Section 4] the chain, and the back-ends agreeing");

        var query = select(CUSTOMER, AMOUNT, STATUS)
                .from("orders")
                .where(STATUS.eq("PAID").or(STATUS.eq("SHIPPED")));

        var sql = query.toSql();
        String expectedSql = "SELECT orders.customer, orders.amount, orders.status FROM orders "
                + "WHERE (orders.status = ? OR orders.status = ?)";
        List<Object> expectedBinds = List.of("PAID", "SHIPPED");

        var reference = ROWS.stream()
                .filter(o -> o.status().equals("PAID") || o.status().equals("SHIPPED"))
                .<Map<String, Object>>map(o -> {
                    var row = new LinkedHashMap<String, Object>();
                    row.put("customer", o.customer());
                    row.put("amount",   o.amount());
                    row.put("status",   o.status());
                    return row;
                })
                .toList();

        boolean sqlOk   = sql.text().equals(expectedSql);
        boolean bindsOk = sql.binds().equals(expectedBinds);
        boolean rowsOk  = query.execute(ROWS).equals(reference);

        System.out.println("  SQL   = " + sql.text());
        System.out.println("  binds = " + sql.binds());
        System.out.println("  rows  :");
        query.execute(ROWS).forEach(row -> System.out.println("    " + row));
        System.out.println("  SQL matches reference?   " + sqlOk);
        System.out.println("  binds match reference?   " + bindsOk);
        System.out.println("  rows match reference?    " + rowsOk);
        System.out.println("  all checks pass?         " + (sqlOk && bindsOk && rowsOk));
    }

    public static void main(String[] args) {
        typedColumns();
        conditionIsATree();
        twoInterpreters();
        endToEnd();
        System.out.println("Mod004AstInterpreterDsl finished");
    }
}
