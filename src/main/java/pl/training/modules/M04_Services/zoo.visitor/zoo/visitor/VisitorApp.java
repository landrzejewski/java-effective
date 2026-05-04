package pl.training.modules.M04_Services.zoo.visitor.zoo.visitor;

import java.util.ServiceLoader;
import pl.training.modules.M04_Services.zoo.tours.api.zoo.tours.api.Tour;

public class VisitorApp {
    public static void main(String... args) {
        // The visitor never imports any provider class.
        // ServiceLoader walks the module path and surfaces every Tour implementation
        // whose module declares `provides zoo.tours.api.Tour with ...`.
        ServiceLoader.load(Tour.class).stream()
                .map(ServiceLoader.Provider::get)
                .sorted((a, b) -> a.name().compareTo(b.name()))
                .forEach(t -> System.out.printf(
                        "- %-12s : %d min  → souvenir: '%s'%n",
                        "'" + t.name() + "'", t.lengthMinutes(), t.souvenir().description()));
    }
}
