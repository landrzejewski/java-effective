package pl.training.functionalprogramming;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

public final class Mod010IoMonadAndCapstone {

    private Mod010IoMonadAndCapstone() {}

    // =================================================================================================
    // IO monad
    // =================================================================================================

    public record Unit() {}
    public static final Unit UNIT = new Unit();

    public interface IO<A> {
        A unsafeRun();

        default <B> IO<B> map(Function<A, B> f) {
            return () -> f.apply(this.unsafeRun());
        }

        default <B> IO<B> flatMap(Function<A, IO<B>> f) {
            return () -> f.apply(this.unsafeRun()).unsafeRun();
        }

        static <A> IO<A> pure(A value) { return () -> value; }
        static IO<Unit> effect(Runnable r) { return () -> { r.run(); return UNIT; }; }
        static <A> IO<A> delay(Supplier<A> s) { return s::get; }
    }

    // =================================================================================================
    // Game model — sealed, immutable
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

        public Board place(Position p, Player who) {
            var newRows = new ArrayList<List<Cell>>(3);
            for (int r = 0; r < 3; r++) {
                if (r == p.row()) {
                    var newRow = new ArrayList<Cell>(cells.get(r));
                    newRow.set(p.col(), new Taken(who));
                    newRows.add(List.copyOf(newRow));
                } else {
                    newRows.add(cells.get(r));         // structural sharing of unchanged rows
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
    // Pure game logic
    // =================================================================================================

    static Mod005OptionEitherTry.Option<Player> winnerOf(Board b) {
        // Rows + columns
        for (int i = 0; i < 3; i++) {
            var rowWinner = lineWinner(b.at(new Position(i, 0)), b.at(new Position(i, 1)), b.at(new Position(i, 2)));
            if (rowWinner instanceof Mod005OptionEitherTry.Some<Player>) return rowWinner;
            var colWinner = lineWinner(b.at(new Position(0, i)), b.at(new Position(1, i)), b.at(new Position(2, i)));
            if (colWinner instanceof Mod005OptionEitherTry.Some<Player>) return colWinner;
        }
        // Diagonals
        var diag1 = lineWinner(b.at(new Position(0, 0)), b.at(new Position(1, 1)), b.at(new Position(2, 2)));
        if (diag1 instanceof Mod005OptionEitherTry.Some<Player>) return diag1;
        var diag2 = lineWinner(b.at(new Position(0, 2)), b.at(new Position(1, 1)), b.at(new Position(2, 0)));
        if (diag2 instanceof Mod005OptionEitherTry.Some<Player>) return diag2;
        return Mod005OptionEitherTry.Option.none();
    }

    private static Mod005OptionEitherTry.Option<Player> lineWinner(Cell a, Cell b, Cell c) {
        if (a instanceof Taken(Player pa)
                && b instanceof Taken(Player pb)
                && c instanceof Taken(Player pc)
                && pa.equals(pb) && pb.equals(pc)) {
            return Mod005OptionEitherTry.Option.some(pa);
        }
        return Mod005OptionEitherTry.Option.none();
    }

    static Mod005OptionEitherTry.Option<Outcome> outcomeOf(Board b) {
        var winner = winnerOf(b);
        if (winner instanceof Mod005OptionEitherTry.Some<Player>(Player p))
            return Mod005OptionEitherTry.Option.some(new Winner(p));
        if (b.isFull())
            return Mod005OptionEitherTry.Option.some(new Draw());
        return Mod005OptionEitherTry.Option.none();
    }

    // =================================================================================================
    // The whole game as one IO<Outcome>
    // =================================================================================================

    /** Reads a move from the IO stream of moves and returns IO<Position>. */
    static IO<Position> readMove(Supplier<Position> source) {
        return IO.delay(source);
    }

    static IO<Unit> printBoard(Board b) {
        return IO.effect(() -> System.out.print(b.render()));
    }

    static IO<Outcome> playGame(Board start, Player startPlayer, Supplier<Position> moveSource) {
        return playRecursive(start, startPlayer, moveSource);
    }

    private static IO<Outcome> playRecursive(Board board, Player current, Supplier<Position> moves) {
        return printBoard(board).flatMap(__ -> {
            var settled = outcomeOf(board);
            if (settled instanceof Mod005OptionEitherTry.Some<Outcome>(Outcome o)) {
                return IO.pure(o);
            }
            return readMove(moves).flatMap(pos -> {
                Board next = (board.at(pos) instanceof Empty) ? board.place(pos, current) : board;
                Player nextPlayer = (board.at(pos) instanceof Empty) ? current.opponent() : current;
                return playRecursive(next, nextPlayer, moves);
            });
        });
    }

    // =================================================================================================
    // Sections
    // =================================================================================================

    /*
    Effects as values

    A side effect — print, read input, query a database — runs now in imperative code. The function signature does
    not warn the caller, and mocking it for tests requires DI tricks.

    The IO monad turns effects into values: instead of running them right away, you build a value that describes
    the effect. The program's edge (main) is the only place that actually executes the description by calling
    unsafeRun().

    This buys two things:
    - explicit purity boundary — pure code returns IO; impure code runs IO,
    - testability — substitute a different unsafeRun() (or a different IO description) without changing pure code.
    */
    static void effectsAsValues() {
        System.out.println("[Section 1] effects as values");
        var doNothingYet = IO.effect(() -> System.out.println("  effect ran"));
        System.out.println("  (created the IO; no output yet)");
        doNothingYet.unsafeRun();
    }

    /*
    IO<A>

    interface IO<A> {
        A unsafeRun();
        default <B> IO<B> map(Function<A, B> f);
        default <B> IO<B> flatMap(Function<A, IO<B>> f);
        static <A> IO<A> pure(A value);
        static IO<Unit> effect(Runnable r);
    }

    unsafeRun() is the boundary that "lights the fuse". Composing IOs produces another IO; nothing actually fires
    until that final call.

    For deep flatMap chains a real IO type would trampoline; ours is small so we keep it simple.
    */
    static void ioComposition() {
        System.out.println("[Section 2] IO composition");
        var program = IO.pure(2)
                .map(n -> n + 3)
                .flatMap(n -> IO.delay(() -> n * 10));
        System.out.println("  program.unsafeRun() = " + program.unsafeRun());
    }

    /*
    Why bother in Java

    - Explicit dataflow — the type tells you "this method does IO"; the return value tells you "the IO has not
      happened yet".
    - Testing — substitute a recording unsafeRun() that captures the description; assert on the captured operations
      instead of trapping console output.
    - Composition — map/flatMap produce richer IOs from smaller ones. The whole game in Section 4 is one
      IO<Outcome> value.

    Real-world IO (Cats Effect, ZIO) adds cancellation, async, fibers, and resource management. The core idea —
    "describe, then run" — is the same.
    */
    static void whyBother() {
        System.out.println("[Section 3] why bother in Java");
        var captured = new AtomicReference<>(new ArrayList<String>());
        var program = IO.effect(() -> captured.get().add("step-1"))
                .flatMap(__ -> IO.effect(() -> captured.get().add("step-2")));
        program.unsafeRun();
        System.out.println("  captured effect order = " + captured.get());
    }

    /*
    A tiny tic-tac-toe

    The capstone uses every prior technique:

    - Sealed types for Player, Cell, GameState (Mod005, Mod006).
    - Pattern matching for game-logic case analysis (Mod006, Mod009).
    - Persistent data for the board — a List<List<Cell>>; every move returns a new board, the old one is unchanged
      (Mod004).
    - Recursion + folds to walk rows / columns / diagonals (Mod003).
    - Option for "winner found?" lookups (Mod005).
    - IO monad for input ("the next move") and output (printing the board); fakes the input from a fixed list of
      moves so the demo is deterministic.

    The whole game is a single IO<Outcome> value built once and run once.

    Capstone composition — explicit pointers

    The code below is the closing summary; each piece is annotated with the prior module that introduced it. Read
    as a study guide.
    */
    static void capstoneGame() {
        System.out.println("[Section 4-5] capstone game (replayed deterministically)");

        // Fixed move sequence — X wins along the diagonal (0,0)-(1,1)-(2,2).
        var moves = new ArrayList<Position>(List.of(
                new Position(0, 0),  // X
                new Position(0, 1),  // O
                new Position(1, 1),  // X
                new Position(0, 2),  // O
                new Position(2, 2)   // X — wins!
        ));
        Supplier<Position> source = () -> moves.remove(0);

        IO<Outcome> game = playGame(Board.empty(), new X(), source);
        Outcome outcome = game.unsafeRun();

        System.out.println("  outcome = " + outcome);
    }

    /*
    End-to-end self-check

    Replay a fixed sequence of moves; assert the game ended with the expected Outcome. The replay is determined by
    the input list, so the self-check is repeatable.
    */
    static void endToEnd() {
        System.out.println("[Section 6] capstone self-check");

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
            // suppress per-game printing for the assertion run
            var program = playGameSilent(Board.empty(), new X(), src);
            Outcome got = program.unsafeRun();
            boolean ok = got.equals(p.expected);
            if (!ok) allOk = false;
            System.out.printf("  [%-16s] got %-26s expected %-26s %s%n",
                    p.label, got, p.expected, ok ? "✓" : "✗");
        }
        System.out.println("  all replays match expected? " + allOk);
    }

    private static IO<Outcome> playGameSilent(Board start, Player startPlayer, Supplier<Position> moves) {
        return playSilentRecursive(start, startPlayer, moves);
    }
    private static IO<Outcome> playSilentRecursive(Board board, Player current, Supplier<Position> moves) {
        return IO.delay(() -> {
            Board cur = board; Player who = current;
            while (true) {
                var settled = outcomeOf(cur);
                if (settled instanceof Mod005OptionEitherTry.Some<Outcome>(Outcome o)) return o;
                Position pos = moves.get();
                if (cur.at(pos) instanceof Empty) { cur = cur.place(pos, who); who = who.opponent(); }
            }
        });
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
