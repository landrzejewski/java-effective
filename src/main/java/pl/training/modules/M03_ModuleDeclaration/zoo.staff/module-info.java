module zoo.staff {
    // `requires transitive` re-exports zoo.animal.talks to anyone who requires zoo.staff.
    requires transitive zoo.animal.talks;
    exports zoo.staff;
}
