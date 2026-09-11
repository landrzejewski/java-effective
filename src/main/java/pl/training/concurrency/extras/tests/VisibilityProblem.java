package pl.training.concurrency.extras.tests;

/**
 * The canonical visibility race, in a form you can actually run (see also Mod002 §2 and §3).
 *
 * <p>Two independent things go wrong without synchronisation:
 * <ol>
 *   <li><b>Visibility.</b> The reader may never observe {@code ready == true} at all — the JIT is free to hoist a
 *       non-volatile field read out of the loop, turning it into an infinite spin.</li>
 *   <li><b>Ordering.</b> Even if it does see the flag, it may still see the old {@code result}, because without a
 *       happens-before edge nothing forces the two writes to become visible in program order.</li>
 * </ol>
 *
 * <p>The Java Memory Model does not promise either failure will happen — it promises nothing at all, which is
 * worse. Code that works on your laptop can fail on a different CPU, a different JIT tier, or after an unrelated
 * change.
 */
public class VisibilityProblem {

    private static final long TIMEOUT_NANOS = 1_000_000_000L;

    /** INCORRECT: no synchronisation of any kind. */
    static final class Racy {
        boolean ready;
        int result;

        void write() {
            result = 42;
            ready = true;       // may become visible before result = 42
        }

        /** @return the value observed, or -1 if the flag never became visible */
        int read() {
            long deadline = System.nanoTime() + TIMEOUT_NANOS;
            while (!ready) {
                if (System.nanoTime() > deadline) {
                    return -1;
                }
            }
            return result;      // may still be 0
        }
    }

    /** CORRECT: one volatile write/read pair creates the happens-before edge that orders everything before it. */
    static final class Safe {
        private int result;             // plain field, published by the volatile write below
        private volatile boolean ready;

        void write() {
            result = 42;
            ready = true;
        }

        int read() {
            long deadline = System.nanoTime() + TIMEOUT_NANOS;
            while (!ready) {
                if (System.nanoTime() > deadline) {
                    return -1;
                }
            }
            return result;      // guaranteed to be 42
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // Control run: the writer and the reader are bound to two DIFFERENT objects, so nobody ever
        // writes the flag the reader spins on. -1 here is guaranteed and says nothing about visibility;
        // it is the baseline the two runs below are compared against.
        System.out.println("control (two objects): observed = " + run(new Racy()::write, new Racy()::read)
                + "   (-1 by construction: the reader's flag is never written)");
        var safe = new Safe();
        System.out.println("safe : observed = " + run(safe::write, safe::read) + "   (always 42)");

        // The real experiment: one object, no volatile. -1 means the write never became visible to the
        // reader (typically because the JIT hoisted the non-volatile read out of the loop); 42 means it
        // happened to be seen. Both outcomes are legal, which is the whole point.
        var racy = new Racy();
        System.out.println("racy (same object)  : observed = " + run(racy::write, racy::read)
                + "   (-1 or 42 — unpredictable, and that is the bug)");
    }

    private static int run(Runnable writer, java.util.function.IntSupplier reader) throws InterruptedException {
        var observed = new int[1];
        var readerThread = new Thread(() -> observed[0] = reader.getAsInt(), "reader");
        readerThread.start();
        Thread.sleep(50);
        writer.run();
        readerThread.join();
        return observed[0];
    }
}
