package pl.training.modules.M02_ExportsRequires.zoo.animal.care.zoo.animal.care;

import pl.training.modules.M02_ExportsRequires.zoo.animal.feeding.zoo.animal.feeding.Feeder; // visible because feeding-module exports the package

public class CareApp {
    public static void main(String... args) {
        Feeder.feed("elephant");
        Feeder.feed("penguin");
        System.out.println("Care round complete.");
    }
}
