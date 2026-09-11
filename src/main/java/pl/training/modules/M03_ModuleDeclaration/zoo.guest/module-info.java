module zoo.guest {
    // We require zoo.staff only — and get zoo.animal.talks for free thanks to
    // the `requires transitive` in zoo.staff's module-info.
    requires zoo.staff;
}
