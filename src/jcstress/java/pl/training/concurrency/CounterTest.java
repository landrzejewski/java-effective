package pl.training.concurrency;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

/**
 * Race detection on an unsynchronised counter (Mod014, "Testing concurrent code").
 *
 * <p>A plain JUnit test can only hint at the problem: it runs one interleaving, usually the same one every time.
 * jcstress runs both actors millions of times across cores and reports EVERY outcome it observed, so an
 * interleaving that happens once in a million stops being invisible.
 *
 * <p>How to run it (behind a Maven profile, so an ordinary `mvn test` stays fast):
 * <pre>
 *   ./mvnw -P jcstress package
 *   java --enable-preview -jar target/jcstress.jar -jvmArgs "--enable-preview" -t CounterTest
 * </pre>
 *
 * <p>How to read the results:
 * <ul>
 *   <li>{@code 1, 2} / {@code 2, 1} — the actors ran one after the other and the counter reached 2. Correct.</li>
 *   <li>{@code 1, 1} — BOTH actors read 0 and both wrote 1. One increment vanished: a lost update.</li>
 *   <li>{@code 2, 2} — both actors read the counter after both writes; the counter itself is right (2), but the
 *       pair of reads matches no sequential execution.</li>
 * </ul>
 */
@JCStressTest
@Outcome(id = "1, 2", expect = Expect.ACCEPTABLE, desc = "Sequential execution (1, then 2)")
@Outcome(id = "2, 1", expect = Expect.ACCEPTABLE, desc = "Sequential execution (2, then 1)")
@Outcome(id = "2, 2", expect = Expect.ACCEPTABLE, desc = "Both reads after both writes; counter = 2")
@Outcome(id = "1, 1", expect = Expect.ACCEPTABLE_INTERESTING, desc = "LOST UPDATE - count++ is not atomic")
@Outcome(expect = Expect.ACCEPTABLE_INTERESTING, desc = "Some other unexpected interleaving")
@State
public class CounterTest {

    int counter;

    @Actor
    public void actor1(II_Result result) {
        counter++;              // load, add, store - three separate steps
        result.r1 = counter;
    }

    @Actor
    public void actor2(II_Result result) {
        counter++;
        result.r2 = counter;
    }
}
