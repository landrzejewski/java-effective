package zoo.animal.care;

import zoo.animal.feeding.Feeder; // visible because feeding-module exports the package

public class CareApp {
    public static void main(String... args) {
        Feeder.feed("elephant");
        Feeder.feed("penguin");
        System.out.println("Care round complete.");
    }
}
