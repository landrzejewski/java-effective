package zoo.tours.guide;

import zoo.tours.api.Souvenir;
import zoo.tours.api.Tour;

public class QuietTour implements Tour {
    public QuietTour() {} // public no-arg constructor required by ServiceLoader

    @Override public String name()          { return "Quiet Walk"; }
    @Override public int lengthMinutes()    { return 30; }
    @Override public Souvenir souvenir()    { return new Souvenir("Postcard"); }
}
