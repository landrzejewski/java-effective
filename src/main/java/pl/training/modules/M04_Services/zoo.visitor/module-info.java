import pl.training.modules.M04_Services.zoo.tours.api.zoo.tours.api.Tour;

module zoo.visitor {
    requires zoo.tours.api;
    uses Tour; // opt in to ServiceLoader.load(Tour.class)
}
