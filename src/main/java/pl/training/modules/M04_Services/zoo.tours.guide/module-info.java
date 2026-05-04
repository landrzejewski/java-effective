import pl.training.modules.M04_Services.zoo.tours.api.zoo.tours.api.Tour;
import pl.training.modules.M04_Services.zoo.tours.guide.zoo.tours.guide.QuietTour;

module zoo.tours.guide {
    requires zoo.tours.api;
    provides Tour with QuietTour;
}
