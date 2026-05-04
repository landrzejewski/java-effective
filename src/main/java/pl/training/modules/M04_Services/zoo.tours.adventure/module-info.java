import pl.training.modules.M04_Services.zoo.tours.adventure.zoo.tours.adventure.AdventureTour;
import pl.training.modules.M04_Services.zoo.tours.api.zoo.tours.api.Tour;

module zoo.tours.adventure {
    requires zoo.tours.api;
    provides Tour with AdventureTour;
}
