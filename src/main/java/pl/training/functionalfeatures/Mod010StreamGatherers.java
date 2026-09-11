package pl.training.functionalfeatures;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Gatherer;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

public final class Mod010StreamGatherers {

    private Mod010StreamGatherers() {}

    record Reading(String sensor, int celsius) {}

    private static final List<Reading> READINGS = List.of(
            new Reading("s1", 20), new Reading("s1", 21), new Reading("s2", 30),
            new Reading("s1", 22), new Reading("s2", 31), new Reading("s3", 15),
            new Reading("s2", 29), new Reading("s1", 24));

    /*
    What a Gatherer is (JEP 485, final in Java 24)

    For a decade the Stream API had a closed set of intermediate operations. If your transformation was not
    map, filter, flatMap, distinct, sorted, limit, skip, takeWhile or dropWhile, you had two bad options: give
    up on the pipeline and write a loop, or collect to a list halfway through and start a second stream.

    Stream.gather(gatherer) opens that set. A Gatherer is to intermediate operations what a Collector is to
    terminal ones: a value describing a transformation, which you can name, reuse, and compose.

    The difference from a Collector, which is what makes it useful:

    - A Collector ENDS the pipeline and produces one container. A Gatherer sits in the MIDDLE and produces a
      stream, so map/filter/collect can follow it.
    - A Gatherer is lazy and incremental. It sees elements one at a time and may push results as it goes.
    - A Gatherer may be stateful. That is the point: windowing, running totals, and "distinct by a key" all
      need memory of what came before.
    - A Gatherer may STOP the stream early, which lets a gatherer work on an infinite source.

    Four parts, all optional except the integrator:
      initializer - create the private state.
      integrator  - consume one element, push zero or more downstream, return false to stop the stream.
      combiner    - merge two states so the gatherer can run in parallel. Omit it and the gatherer is
                    sequential-only, which is the honest default for order-dependent logic.
      finisher    - after the last element, push anything still held in the state.
    */
    static void whatIsAGatherer() {
        System.out.println("[Section 1] gatherer sits in the middle of a pipeline");

        // A gatherer is an intermediate step: the pipeline continues after it.
        var result = READINGS.stream()
                .map(Reading::celsius)
                .gather(Gatherers.windowFixed(3))    // Stream<Integer> becomes Stream<List<Integer>>
                .map(window -> window.stream().mapToInt(Integer::intValue).sum())
                .toList();
        System.out.println("  windowed then summed = " + result);
        System.out.println("  a Collector could not do this: it would have ended the pipeline");
    }

    /*
    windowFixed and windowSliding

    - windowFixed(n)   splits the stream into consecutive, non-overlapping lists of n. The final window is
                       whatever is left over, so it may be shorter.
    - windowSliding(n) emits every window of n consecutive elements, advancing by one each time. A stream of k
                       elements yields k - n + 1 windows, and none at all when k < n.

    windowSliding is the idiomatic way to look at pairs of neighbours: deltas, "is this sorted", moving
    averages, detecting a change between consecutive rows. Written by hand, all of those need an index loop
    and a bounds check.
    */
    static void windowing() {
        System.out.println("[Section 2] windowFixed and windowSliding");

        var temps = READINGS.stream().map(Reading::celsius).toList();
        System.out.println("  source            = " + temps);

        System.out.println("  windowFixed(3)    = " + temps.stream().gather(Gatherers.windowFixed(3)).toList());
        System.out.println("  windowSliding(2)  = " + temps.stream().gather(Gatherers.windowSliding(2)).toList());

        // Neighbour deltas: the classic windowSliding(2) job.
        var deltas = temps.stream()
                .gather(Gatherers.windowSliding(2))
                .map(pair -> pair.get(1) - pair.get(0))
                .toList();
        System.out.println("  consecutive deltas = " + deltas);

        // Moving average over 3 readings.
        var moving = temps.stream()
                .gather(Gatherers.windowSliding(3))
                .map(w -> w.stream().mapToInt(Integer::intValue).average().orElseThrow())
                .map(avg -> String.format(Locale.ROOT, "%.1f", avg))
                .toList();
        System.out.println("  moving average(3)  = " + moving);
    }

    /*
    fold and scan

    - fold(initial, folder)  reduces the whole stream to ONE element and emits it at the end. It differs from
                             reduce() in two ways that matter: the result type may differ from the element
                             type without a combiner, and the output is a Stream, so the pipeline continues.
    - scan(initial, folder)  is the incremental version: it emits every intermediate result. A running total,
                             a running maximum, a cumulative balance.

    scan is the one with no equivalent anywhere else in the Stream API. Producing a running total used to
    require an external mutable variable inside a map lambda, which is exactly the stateful-lambda bug that
    breaks under parallelism. scan keeps the state inside the gatherer, where it belongs.
    */
    static void foldAndScan() {
        System.out.println("[Section 3] fold and scan");

        var temps = READINGS.stream().map(Reading::celsius).toList();

        System.out.println("  fold to a sum     = " + temps.stream()
                .gather(Gatherers.fold(() -> 0, Integer::sum)).toList());

        System.out.println("  fold to a String  = " + temps.stream()
                .gather(Gatherers.fold(() -> "", (acc, t) -> acc.isEmpty() ? "" + t : acc + "/" + t)).toList());

        System.out.println("  scan running sum  = " + temps.stream()
                .gather(Gatherers.scan(() -> 0, Integer::sum)).toList());

        System.out.println("  scan running max  = " + temps.stream()
                .gather(Gatherers.scan(() -> Integer.MIN_VALUE, Integer::max)).toList());

        // And because scan is an intermediate operation, the pipeline goes on.
        System.out.println("  first partial sum over 100 = " + temps.stream()
                .gather(Gatherers.scan(() -> 0, Integer::sum))
                .filter(sum -> sum > 100)
                .findFirst().orElseThrow());
    }

    /*
    mapConcurrent

    mapConcurrent(maxConcurrency, fn) applies fn to each element on its own VIRTUAL thread, with at most
    maxConcurrency running at a time, and preserves the encounter order of the results.

    This is the piece the Stream API was missing for I/O. A parallel() stream uses the ForkJoinPool common
    pool, which is sized for CPU work and is the wrong place to block on a network call. mapConcurrent runs
    each call on a virtual thread, where blocking is cheap, and gives you an explicit concurrency limit
    instead of a global pool.

    Semantics worth knowing: the stream stays sequential (only fn runs concurrently), order is preserved, and
    if fn throws, the remaining tasks are cancelled and the exception propagates.

    For CPU-bound work, use parallel() instead. pl.training.concurrency.Mod010ParallelStreams covers when that
    pays off, and Mod011VirtualThreads covers the threads underneath this operation.
    */
    static void mapConcurrent() {
        System.out.println("[Section 4] mapConcurrent for blocking work");

        var start = System.nanoTime();
        var fetched = Stream.of("s1", "s2", "s3", "s4", "s5", "s6")
                .gather(Gatherers.mapConcurrent(4, Mod010StreamGatherers::slowLookup))
                .toList();
        var millis = (System.nanoTime() - start) / 1_000_000;

        System.out.println("  results (in order) = " + fetched);
        System.out.println("  6 x 100ms of blocking, 4 at a time, took about " + millis + "ms");
        System.out.println("  sequentially it would have been about 600ms");
    }

    private static String slowLookup(String sensor) {
        try {
            Thread.sleep(100);                       // stands in for a network or database call
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return sensor.toUpperCase();
    }

    /*
    Writing your own Gatherer

    Two factories:

    - Gatherer.ofSequential(initializer, integrator [, finisher]) for order-dependent logic. No combiner, so
      the gatherer refuses to run in parallel rather than producing a wrong answer quietly.
    - Gatherer.of(initializer, integrator, combiner, finisher) when the state really can be merged.

    The integrator is (state, element, downstream) and returns a boolean: true to continue, false to stop the
    upstream. Returning false is how a gatherer short-circuits, which is what lets one work on an infinite
    stream. Integrator.ofGreedy(...) declares "I never return false", which lets the implementation optimise.

    downstream.push(value) returns false when the consumer downstream has stopped caring (a limit() below,
    for instance), so a well-behaved integrator propagates that.
    */
    static void writingAGatherer() {
        System.out.println("[Section 5] writing a Gatherer");

        // distinctBy: like distinct(), but on an extracted key. There is still nothing in the JDK for this.
        System.out.println("  distinctBy(sensor) = " + READINGS.stream()
                .gather(distinctBy(Reading::sensor))
                .map(r -> r.sensor() + ":" + r.celsius())
                .toList());

        // A short-circuiting gatherer, running on an INFINITE stream. It stops the source itself.
        System.out.println("  untilSumExceeds(50) over 1,2,3,... = " + Stream.iterate(1, i -> i + 1)
                .gather(untilSumExceeds(50))
                .toList());

        // A gatherer with a finisher: emit the leftovers held in the state after the last element.
        System.out.println("  chunkOnChange(sensor) = " + READINGS.stream()
                .gather(chunkOnChange(Reading::sensor))
                .map(chunk -> chunk.getFirst().sensor() + "x" + chunk.size())
                .toList());
    }

    /* Emit an element only the first time its key is seen. State: the set of keys already emitted. */
    private static <T, K> Gatherer<T, ?, T> distinctBy(Function<? super T, ? extends K> keyFn) {
        return Gatherer.ofSequential(
                HashSet<K>::new,
                (seen, element, downstream) -> seen.add(keyFn.apply(element)) ? downstream.push(element) : true);
    }

    /* Pass elements through until their running sum exceeds the limit, then stop the upstream. */
    private static Gatherer<Integer, ?, Integer> untilSumExceeds(int limit) {
        return Gatherer.ofSequential(
                () -> new int[1],
                (sum, element, downstream) -> {
                    sum[0] += element;
                    if (sum[0] > limit) {
                        return false;                // false stops the stream, infinite source included
                    }
                    return downstream.push(element);
                });
    }

    /* Group consecutive elements that share a key. The finisher flushes the last, still-open chunk. */
    private static <T, K> Gatherer<T, ?, List<T>> chunkOnChange(Function<? super T, ? extends K> keyFn) {
        return Gatherer.ofSequential(
                ArrayList<T>::new,
                (chunk, element, downstream) -> {
                    if (!chunk.isEmpty() && !keyFn.apply(chunk.getLast()).equals(keyFn.apply(element))) {
                        var completed = List.copyOf(chunk);
                        chunk.clear();
                        if (!downstream.push(completed)) {
                            return false;
                        }
                    }
                    chunk.add(element);
                    return true;
                },
                (chunk, downstream) -> {
                    if (!chunk.isEmpty()) {
                        downstream.push(List.copyOf(chunk));
                    }
                });
    }

    /*
    Gatherer or something else

    | Need                                          | Reach for                       |
    |-----------------------------------------------|---------------------------------|
    | one in, one out                               | map                             |
    | keep or drop                                  | filter                          |
    | one in, many out, no memory of past elements  | flatMap, or mapMulti (Mod009)   |
    | needs memory of previous elements             | gather                          |
    | needs to stop the source early                | gather, or takeWhile if the test is per-element |
    | concurrent blocking calls, order preserved    | Gatherers.mapConcurrent         |
    | one final container                           | collect                         |

    Gatherers compose with each other through andThen(other), so a windowing gatherer followed by a scanning
    one is a single named value you can pass around and test on its own. That composability is the reason to
    prefer a gatherer over an equivalent hand-written loop, quite apart from the pipeline staying intact.
    */
    static void composing() {
        System.out.println("[Section 6] composing gatherers");

        // One value that means "pairs of neighbours", built from two gatherers.
        Gatherer<Integer, ?, Integer> deltas = Gatherers.<Integer>windowSliding(2)
                .andThen(Gatherer.of((a, pair, down) -> down.push(pair.get(1) - pair.get(0))));

        System.out.println("  composed deltas    = " + READINGS.stream()
                .map(Reading::celsius).gather(deltas).toList());

        System.out.println("  running sum of them= " + READINGS.stream()
                .map(Reading::celsius).gather(deltas).gather(Gatherers.scan(() -> 0, Integer::sum)).toList());
    }

    public static void main(String[] args) {
        whatIsAGatherer();
        windowing();
        foldAndScan();
        mapConcurrent();
        writingAGatherer();
        composing();
        System.out.println("Mod010StreamGatherers finished");
    }
}
