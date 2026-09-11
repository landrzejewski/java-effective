package pl.training.functionalprogramming;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

import pl.training.functionalprogramming.Mod005OptionEitherTry.Option;
import pl.training.functionalprogramming.Mod005OptionEitherTry.Some;

/*
The IO monad, and a capstone that uses everything

Mod001 said: push effects to the edges, keep the middle pure, make the boundary visible. Every module
since has been about the middle. This one is about the boundary.

THE PROBLEM. In ordinary Java, an effect happens at the moment you write it. System.out.println("hi")
prints. A repository call queries. And the signature tells you nothing: `void save(Order o)` and
`void validate(Order o)` look identical, yet one touches a database and the other does not. Testing the
first means dependency injection, a mock, and a framework to wire them together.

THE MOVE. Do not perform the effect - describe it. An IO<A> is a value that says "when someone
eventually runs me, I will produce an A, possibly by doing something to the world". Building one does
nothing at all. The effect happens exactly once, at exactly one place: the call to unsafeRun() at the
program's edge.

WHAT IO ACTUALLY IS. Strip away the name and IO<A> is a Supplier<A>:

    interface IO<A> { A unsafeRun(); }

That is the whole type. One method, no arguments, returns an A. It is a functional interface, which is
why every factory below is a one-line lambda. There is no runtime, no magic, no bytecode trickery - the
entire mechanism is "wrap it in a lambda so it does not run yet", exactly like the thunks of Mod007 and
the recipes of Mod008. Those three modules are the same idea applied to three different things:

    Mod007  Supplier<A>              defer a VALUE
    Mod008  S -> (A, S)              thread a STATE
    Mod010  () -> A (with effects)   defer an EFFECT

So why bother with a wrapper around Supplier? Because of what you can then do to it:

- The type is honest. `IO<Unit> save(Order o)` says "this does IO, and it has not happened yet". You can
  see it in the signature without reading the body.
- map and flatMap build bigger descriptions out of smaller ones, so an entire program - including its
  control flow - becomes a single value you can pass around, return from a method, or store in a field.
- The interpreter is swappable. Because the effect is only a description, you can hand a different
  implementation to the same pure code and get a recording, a stub or a fake. Section 3 does exactly
  that: the same game logic, run once printing to the console and once recording into a list, with no
  mocking framework and no change to the logic.

Section 4 is the capstone: a complete tic-tac-toe game as one IO<Outcome> value, built from the
techniques of every previous module.
*/

public final class Mod010IoMonadAndCapstone {

    private Mod010IoMonadAndCapstone() {}

    // =================================================================================================
    // IO monad
    // =================================================================================================

    /** The "no interesting value" type - Java's void, as an actual value you can return and store.
     *  Needed because IO<A> must produce SOMETHING, and printing produces nothing worth having. */
    public record Unit() {}
    public static final Unit UNIT = new Unit();

    public interface IO<A> {

        /** The only method: run the description and produce its value, performing any effects on the way.
         *  Deliberately named "unsafe" - it is the one place purity ends, and it should appear once, in
         *  main, and essentially nowhere else. */
        A unsafeRun();

        /** Transform the eventual result. Note what this does NOT do: it does not run anything. It
         *  returns a NEW description whose eventual result is f applied to this one's. */
        default <B> IO<B> map(Function<A, B> f) {
            return () -> f.apply(this.unsafeRun());
        }

        /** Sequence two effects, where the second depends on the first one's result.
         *  Same shape as every other flatMap in this package: run this, use the value to choose the next
         *  description, then run that one. The ordering of effects falls out of the nesting - which is
         *  how a language with no statements still gets "do this, then that". */
        default <B> IO<B> flatMap(Function<A, IO<B>> f) {
            return () -> f.apply(this.unsafeRun()).unsafeRun();
        }

        /** Wrap a value that already exists. No effect at all. */
        static <A> IO<A> pure(A value) { return () -> value; }
        /** Describe a side effect that produces nothing worth returning. */
        static IO<Unit> effect(Runnable r) { return () -> { r.run(); return UNIT; }; }
        /** Defer any computation. This one makes it obvious that IO IS a Supplier: the body is `s::get`. */
        static <A> IO<A> delay(Supplier<A> s) { return s::get; }
    }

    // =================================================================================================
    // Game model - sealed and immutable throughout (Mod001, Mod004)
    // =================================================================================================

    public sealed interface Player permits X, O {
        default Player opponent() { return this instanceof X ? new O() : new X(); }
    }
    public record X() implements Player { @Override public String toString() { return "X"; } }
    public record O() implements Player { @Override public String toString() { return "O"; } }

    public sealed interface Cell permits Empty, Taken {}
    public record Empty()              implements Cell { @Override public String toString() { return "."; } }
    public record Taken(Player player) implements Cell { @Override public String toString() { return player.toString(); } }

    public record Position(int row, int col) {}

    public record Board(List<List<Cell>> cells) {

        public static Board empty() {
            var rows = new ArrayList<List<Cell>>(3);
            for (int r = 0; r < 3; r++) {
                var row = new ArrayList<Cell>(3);
                for (int c = 0; c < 3; c++) row.add(new Empty());
                rows.add(List.copyOf(row));
            }
            return new Board(List.copyOf(rows));
        }

        public Cell at(Position p) { return cells.get(p.row()).get(p.col()); }

        /** A move returns a NEW board; the old one is untouched and still valid. Note the else branch:
         *  the two rows that did not change are reused by reference, not copied - structural sharing,
         *  exactly as in Mod004. Placing a stone allocates one row, not a whole board. */
        public Board place(Position p, Player who) {
            var newRows = new ArrayList<List<Cell>>(3);
            for (int r = 0; r < 3; r++) {
                if (r == p.row()) {
                    var newRow = new ArrayList<Cell>(cells.get(r));
                    newRow.set(p.col(), new Taken(who));
                    newRows.add(List.copyOf(newRow));
                } else {
                    newRows.add(cells.get(r));         // shared, not copied
                }
            }
            return new Board(List.copyOf(newRows));
        }

        public boolean isFull() {
            for (var row : cells) for (var c : row) if (c instanceof Empty) return false;
            return true;
        }

        public String render() {
            var sb = new StringBuilder();
            for (var row : cells) {
                for (var c : row) sb.append(c).append(' ');
                sb.append('\n');
            }
            return sb.toString();
        }
    }

    public sealed interface Outcome permits Winner, Draw {}
    public record Winner(Player player) implements Outcome {}
    public record Draw()                implements Outcome {}

    // =================================================================================================
    // Pure game logic - no IO anywhere in this section
    //
    // Every method here is a pure function of a Board. They can be tested by constructing a board and
    // asserting on the result: no console, no input, no setup. That is the whole point of keeping the
    // effects out of them.
    // =================================================================================================

    /** Option is the right return type here (Mod005): "no winner yet" is a normal state, not an error,
     *  and there is nothing useful to say about why. */
    static Option<Player> winnerOf(Board b) {
        for (int i = 0; i < 3; i++) {
            var rowWinner = lineWinner(b.at(new Position(i, 0)), b.at(new Position(i, 1)), b.at(new Position(i, 2)));
            if (rowWinner instanceof Some<Player>) return rowWinner;
            var colWinner = lineWinner(b.at(new Position(0, i)), b.at(new Position(1, i)), b.at(new Position(2, i)));
            if (colWinner instanceof Some<Player>) return colWinner;
        }
        var diag1 = lineWinner(b.at(new Position(0, 0)), b.at(new Position(1, 1)), b.at(new Position(2, 2)));
        if (diag1 instanceof Some<Player>) return diag1;
        var diag2 = lineWinner(b.at(new Position(0, 2)), b.at(new Position(1, 1)), b.at(new Position(2, 0)));
        if (diag2 instanceof Some<Player>) return diag2;
        return Option.none();
    }

    /** Record deconstruction patterns doing the work: three cells are a win if all three are Taken by
     *  the same player, and that whole condition is one `instanceof` chain with no casts and no getters. */
    private static Option<Player> lineWinner(Cell a, Cell b, Cell c) {
        if (a instanceof Taken(Player pa)
                && b instanceof Taken(Player pb)
                && c instanceof Taken(Player pc)
                && pa.equals(pb) && pb.equals(pc)) {
            return Option.some(pa);
        }
        return Option.none();
    }

    /** None means "the game is still running" - which is why Option is a better fit here than a
     *  three-valued enum or a nullable Outcome. */
    static Option<Outcome> outcomeOf(Board b) {
        var winner = winnerOf(b);
        if (winner instanceof Some<Player>(Player p)) return Option.some(new Winner(p));
        if (b.isFull()) return Option.some(new Draw());
        return Option.none();
    }

    // =================================================================================================
    // The whole game as one IO<Outcome>
    // =================================================================================================

    static IO<Unit> printBoard(Board b) {
        return IO.effect(() -> System.out.print(b.render()));
    }

    /** A renderer that shows nothing. Used by the self-check, so the assertions run against the exact
     *  same game logic as the demo - just with a different interpretation of "display the board".
     *  The parameter is deliberately ignored; it cannot be written as `_` because unnamed variables
     *  (Java 22) cover locals, lambda parameters, patterns and catch clauses - but not method
     *  parameters, whose names are part of the signature. */
    static IO<Unit> renderNothing(Board board) {
        return IO.pure(UNIT);
    }

    /**
     * The complete game, as a value.
     *
     * Both effects are parameters rather than hard-coded calls:
     *   moveSource - where the next move comes from (a human, a list, a bot)
     *   render     - what "show the board" means (print it, record it, ignore it)
     *
     * This is dependency injection with no framework and no interfaces to declare - the dependencies are
     * just functions, and substituting them is what makes the pure logic testable. Section 3 and the
     * self-check both exploit it.
     *
     * Nothing runs when you call this. It returns a description of the entire game.
     */
    static IO<Outcome> playGame(Board start, Player startPlayer,
                                Supplier<Position> moveSource,
                                Function<Board, IO<Unit>> render) {
        return playRecursive(start, startPlayer, moveSource, render);
    }

    /*
    One turn, expressed as IO, calling itself for the next turn.

    Read it as three steps glued by flatMap:
      1. render the board,
      2. if the game is over, produce the outcome and stop (IO.pure - no further effects),
      3. otherwise read a move, compute the NEW board purely, and describe the rest of the game.

    Note that the recursion builds a nested description rather than running turns. Nothing has happened
    when this method returns; the whole game is now a single value waiting for unsafeRun(). Note also that
    the board is never mutated - each turn hands a new Board to the next one.
    */
    private static IO<Outcome> playRecursive(Board board, Player current,
                                             Supplier<Position> moves,
                                             Function<Board, IO<Unit>> render) {
        return render.apply(board).flatMap(_ -> {
            var settled = outcomeOf(board);
            if (settled instanceof Some<Outcome>(Outcome o)) {
                return IO.pure(o);                          // finished: no more effects to describe
            }
            return IO.delay(moves::get).flatMap(pos -> {
                boolean free = board.at(pos) instanceof Empty;
                Board next        = free ? board.place(pos, current) : board;
                Player nextPlayer = free ? current.opponent()        : current;
                return playRecursive(next, nextPlayer, moves, render);
            });
        });
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    /*
    Section 1 - effects as values

    The demo does the smallest possible version of the whole idea: build an IO that prints, observe that
    nothing has printed, then run it.

    Look at the output order. The line "created the IO - nothing printed yet" appears BEFORE the effect's
    own output, even though the effect was written first in the source. That gap between describing and
    performing is the only thing IO does, and everything else in this module is a consequence of it.

    Two things it buys:

    - An explicit purity boundary. Pure code RETURNS IO values; only the edge RUNS them. A method that
      returns IO<Unit> is honest about doing IO in a way that a void method never is.
    - Substitutable interpretation. The description is data, so something other than the real world can
      consume it (Section 3).
    */
    static void effectsAsValues() {
        System.out.println("[Section 1] effects as values");
        var greet = IO.effect(() -> System.out.println("    <- the effect is running NOW"));
        System.out.println("  created the IO - nothing printed yet");
        greet.unsafeRun();
        System.out.println("  ...and running it twice runs the effect twice:");
        greet.unsafeRun();
    }

    /*
    Section 2 - IO<A> and its combinators

        interface IO<A> {
            A unsafeRun();
            default <B> IO<B> map(Function<A, B> f);
            default <B> IO<B> flatMap(Function<A, IO<B>> f);
            static  <A> IO<A> pure(A value);
            static      IO<Unit> effect(Runnable r);
            static  <A> IO<A> delay(Supplier<A> s);
        }

    map transforms the eventual result; flatMap sequences two effects where the second depends on the
    first. Composing IOs always produces another IO - the chain grows, nothing fires - until unsafeRun()
    lights the fuse at the end.

    Notice how ordinary this is. flatMap here has the same body as flatMap on Option, on Try, on State:
    unwrap, apply the function, hand back the result. Once you can read one of them you can read all of
    them, which is the practical reason the word "monad" is worth knowing.

    ONE REAL LIMITATION. Look at flatMap's body: it calls unsafeRun() inside a lambda, which means a
    chain of N flatMaps nests N real Java stack frames when it finally runs. A few thousand is fine; a
    loop over a large collection is a StackOverflowError. This is exactly the missing tail-call problem
    from Mod003, and production IO types (Cats Effect, ZIO, Vavr) solve it with exactly the technique
    from Mod003 Section 5: they trampoline, representing the chain as heap objects driven by one loop
    instead of nested frames. Our version is small and left un-trampolined on purpose; tic-tac-toe never
    exceeds nine turns.
    */
    static void ioComposition() {
        System.out.println("[Section 2] IO composition");
        var program = IO.pure(2)
                .map(n -> n + 3)
                .flatMap(n -> IO.delay(() -> n * 10));
        System.out.println("  built a 3-step program; nothing has run");
        System.out.println("  program.unsafeRun() = " + program.unsafeRun());
        System.out.println("  program.unsafeRun() = " + program.unsafeRun() + "   <- a description is reusable");
    }

    /*
    Section 3 - why bother in Java: swapping the interpreter

    This is the section that earns the abstraction, so the demo does the real thing rather than describing
    it.

    The SAME game - the same playGame call, the same pure logic, the same move sequence - is run twice
    with two different renderers:

      printBoard      writes to System.out
      recording       appends a one-line summary to a List<String>

    Neither the game logic nor the move source changes. There is no mock, no framework, no interface to
    implement, no @Before setup: the effect was a parameter all along, so substituting it is just passing
    a different function.

    Compare with what testing this would need if playGame called System.out.print directly: capture
    System.out by swapping a global, parse the captured text back into something assertable, and remember
    to restore the stream afterwards. The IO version asserts on a List<String>, which is data.

    That is the whole practical argument for IO in Java. Not purity for its own sake - the ability to
    decide, at the call site, what the effects actually mean.
    */
    static void whyBother() {
        System.out.println("[Section 3] why bother - the same program, two interpreters");

        var log = new ArrayList<String>();
        Function<Board, IO<Unit>> recording =
                b -> IO.effect(() -> log.add("board with " + countTaken(b) + " stones placed"));

        var moves = new ArrayList<>(List.of(
                new Position(0, 0), new Position(0, 1), new Position(1, 1),
                new Position(0, 2), new Position(2, 2)));
        Supplier<Position> src = () -> moves.remove(0);

        // Exactly the same call as the console demo in Section 4 - only `render` differs.
        Outcome outcome = playGame(Board.empty(), new X(), src, recording).unsafeRun();

        System.out.println("  outcome            = " + outcome);
        System.out.println("  recorded " + log.size() + " render calls, with no console output:");
        for (var line : log) System.out.println("    " + line);
        System.out.println("  ^ assertions can now run against a List<String> instead of captured stdout");
    }

    private static int countTaken(Board b) {
        int n = 0;
        for (var row : b.cells()) for (var c : row) if (c instanceof Taken) n++;
        return n;
    }

    /*
    Section 4 - the capstone: tic-tac-toe as a single value

    Every technique in the package, in one small program:

    - Mod001  Immutability and purity - Board.place returns a new board; all the game logic is pure.
    - Mod004  Structural sharing - a move rebuilds one row and shares the other two by reference.
    - Mod005  Option for "is there a winner yet", where absence is a normal state rather than an error.
    - Mod005  Sealed interfaces for Player, Cell and Outcome: a closed set of cases, eliminated here with
              record-deconstruction instanceof (a switch would be checked for exhaustiveness too).
    - Mod002  Functions as parameters - moveSource and render are injected, not hard-coded.
    - Mod007  Deferred evaluation - the same "wrap it in a lambda" trick, applied to effects.
    - Mod010  IO for input and output, so the entire game is one value.

    Record deconstruction patterns (`b instanceof Taken(Player pb)`) do the case analysis throughout,
    which is what keeps lineWinner down to one readable condition.

    The demo replays a fixed move list so the run is deterministic. Substituting a Scanner for the list
    would make it interactive, and not one line of game logic would change - which is the point.
    */
    static void capstoneGame() {
        System.out.println("[Section 4] capstone game (replayed deterministically)");

        // X wins along the diagonal (0,0)-(1,1)-(2,2).
        var moves = new ArrayList<>(List.of(
                new Position(0, 0),  // X
                new Position(0, 1),  // O
                new Position(1, 1),  // X
                new Position(0, 2),  // O
                new Position(2, 2)   // X - wins
        ));
        Supplier<Position> source = () -> moves.remove(0);

        // Building the value runs nothing at all.
        IO<Outcome> game = playGame(Board.empty(), new X(), source, Mod010IoMonadAndCapstone::printBoard);
        System.out.println("  the whole game is now one IO<Outcome> value. Running it:");

        Outcome outcome = game.unsafeRun();     // the single point where anything happens
        System.out.println("  outcome = " + outcome);
    }

    /*
    Section 5 - end-to-end self-check

    Three fixed move sequences, each replayed and checked against its expected Outcome: a diagonal win
    for X, a row win for O, and a full board with no line, which must be a Draw.

    The important detail is that these replays call the SAME playGame as the Section 4 demo, with the
    same pure logic and the same recursion - only the renderer differs, and it is renderNothing so the
    assertions are not buried in board output. A self-check that exercised a separate, quieter copy of
    the game logic would prove nothing about the code you actually read.

    That the tests can reuse the real implementation, changing only which effects it performs, is the
    concrete payoff of Section 3.
    */
    static void endToEnd() {
        System.out.println("[Section 5] capstone self-check");

        record Probe(String label, List<Position> moves, Outcome expected) {}
        var probes = List.of(
                new Probe("X wins diagonal",
                        List.of(new Position(0, 0), new Position(0, 1),
                                new Position(1, 1), new Position(0, 2),
                                new Position(2, 2)),
                        new Winner(new X())),
                new Probe("O wins row",
                        List.of(new Position(0, 0), new Position(1, 0),
                                new Position(0, 1), new Position(1, 1),
                                new Position(2, 2), new Position(1, 2)),
                        new Winner(new O())),
                new Probe("Draw",
                        List.of(new Position(0, 0), new Position(0, 1),
                                new Position(0, 2), new Position(1, 1),
                                new Position(1, 0), new Position(1, 2),
                                new Position(2, 1), new Position(2, 0),
                                new Position(2, 2)),
                        new Draw()));

        boolean allOk = true;
        for (var p : probes) {
            var queue = new ArrayList<>(p.moves);
            Supplier<Position> src = () -> queue.remove(0);

            // The production implementation, with the display effect swapped out.
            Outcome got = playGame(Board.empty(), new X(), src,
                    Mod010IoMonadAndCapstone::renderNothing).unsafeRun();

            boolean ok = got.equals(p.expected);
            if (!ok) allOk = false;
            System.out.printf(Locale.ROOT, "  [%-16s] got %-26s expected %-26s %s%n",
                    p.label, got, p.expected, ok ? "✓" : "✗");
        }
        System.out.println("  all replays match expected? " + allOk);
    }

    public static void main(String[] args) {
        effectsAsValues();
        ioComposition();
        whyBother();
        capstoneGame();
        endToEnd();
        System.out.println("Mod010IoMonadAndCapstone finished");
    }
}
