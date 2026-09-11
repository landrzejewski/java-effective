package pl.training.concurrency.extras.solution2;

public class Application {

    private static final int MOLECULES = 8;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("[solution2] assembling " + MOLECULES + " H2O molecules");

        var factory = new Factory();
        var hydrogen = new Thread(new Producer(factory, "H", 2, MOLECULES * 2), "hydrogen");
        var oxygen = new Thread(new Producer(factory, "O", 1, MOLECULES), "oxygen");

        hydrogen.start();
        oxygen.start();
        hydrogen.join();
        oxygen.join();

        System.out.println("  molecules produced = " + factory.moleculesProduced() + "/" + MOLECULES);
    }
}
