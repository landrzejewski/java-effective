package pl.training.modules.M04_Services.zoo.tours.guide.zoo.tours.guide;

import pl.training.modules.M04_Services.zoo.tours.api.zoo.tours.api.Souvenir;
import pl.training.modules.M04_Services.zoo.tours.api.zoo.tours.api.Tour;

public class QuietTour implements Tour {
    public QuietTour() {} // public no-arg constructor required by ServiceLoader

    @Override public String name()          { return "Quiet Walk"; }
    @Override public int lengthMinutes()    { return 30; }
    @Override public Souvenir souvenir()    { return new Souvenir("Postcard"); }
}
