package pl.training.concurrency.extras.solution1;

import pl.training.concurrency.extras.common.ThreadUtils;

import java.util.ArrayList;

/**
 * The unisex-bathroom problem: men and women may not be inside at the same time, and at most three people fit.
 *
 * <p>Watch the output for the property this solution does NOT have: a steady stream of one gender can keep the
 * other waiting indefinitely (starvation, Mod014 §5). Fixing that needs an explicit "someone is waiting" counter
 * that makes arrivals of the current gender stand down — worth doing as an exercise.
 */
public class Application {

    public static void main(String[] args) throws InterruptedException {
        var bathroom = new Bathroom();
        var people = new ArrayList<Thread>();

        people.add(ThreadUtils.asyncRun("Anna", () -> bathroom.femaleUseBathroom("Anna")));
        people.add(ThreadUtils.asyncRun("Jan", () -> bathroom.maleUseBathroom("Jan")));
        people.add(ThreadUtils.asyncRun("Marek", () -> bathroom.maleUseBathroom("Marek")));
        people.add(ThreadUtils.asyncRun("Marta", () -> bathroom.femaleUseBathroom("Marta")));
        people.add(ThreadUtils.asyncRun("Adam", () -> bathroom.maleUseBathroom("Adam")));
        people.add(ThreadUtils.asyncRun("Michal", () -> bathroom.maleUseBathroom("Michal")));

        ThreadUtils.startAndJoin(people);
        System.out.println("everyone is done");
    }
}
