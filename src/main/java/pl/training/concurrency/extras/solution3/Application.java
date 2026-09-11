package pl.training.concurrency.extras.solution3;

import pl.training.concurrency.extras.common.ThreadUtils;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class Application {

    private static final int CHAIRS = 3;
    private static final int CUSTOMERS = 12;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("[solution3] sleeping barber, " + CHAIRS + " chairs, " + CUSTOMERS + " customers");

        var barberShop = new BarberShop(CHAIRS);
        var barberThread = ThreadUtils.asyncRun("barber", barberShop::barber);
        barberThread.start();

        var customers = new ArrayList<Thread>();
        for (int index = 0; index < CUSTOMERS; index++) {
            var customer = ThreadUtils.asyncRun("customer-" + index, barberShop::customerWalksIn);
            customers.add(customer);
            customer.start();
            Thread.sleep(ThreadLocalRandom.current().nextInt(150));
        }
        for (var customer : customers) {
            customer.join();
        }

        // The barber blocks forever waiting for the next customer, so cancel him explicitly.
        // Without this the JVM would never exit — a non-daemon thread parked on acquire().
        barberThread.interrupt();
        barberThread.join();

        System.out.println("  haircuts given = " + barberShop.hairCutsGiven()
                + ", customers turned away = " + barberShop.customersTurnedAway());
    }
}
