import zoo.tours.api.Tour;
import zoo.tours.guide.QuietTour;

module zoo.tours.guide {
    requires zoo.tours.api;
    provides Tour with QuietTour;
}
