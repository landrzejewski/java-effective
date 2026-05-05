import zoo.tours.adventure.AdventureTour;
import zoo.tours.api.Tour;

module zoo.tours.adventure {
    requires zoo.tours.api;
    provides Tour with AdventureTour;
}
