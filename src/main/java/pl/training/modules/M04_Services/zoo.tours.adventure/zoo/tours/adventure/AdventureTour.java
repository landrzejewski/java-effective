package zoo.tours.adventure;

import zoo.tours.api.Souvenir;
import zoo.tours.api.Tour;

public class AdventureTour implements Tour {
    public AdventureTour() {}

    @Override public String name()          { return "Adventure"; }
    @Override public int lengthMinutes()    { return 90; }
    @Override public Souvenir souvenir()    { return new Souvenir("T-Shirt"); }
}
