package pl.training.modules.M04_Services.zoo.tours.adventure.zoo.tours.adventure;

import pl.training.modules.M04_Services.zoo.tours.api.zoo.tours.api.Souvenir;
import pl.training.modules.M04_Services.zoo.tours.api.zoo.tours.api.Tour;

public class AdventureTour implements Tour {
    public AdventureTour() {}

    @Override public String name()          { return "Adventure"; }
    @Override public int lengthMinutes()    { return 90; }
    @Override public Souvenir souvenir()    { return new Souvenir("T-Shirt"); }
}
