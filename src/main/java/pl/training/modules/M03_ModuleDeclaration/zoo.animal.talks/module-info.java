module zoo.animal.talks {
    // qualified export — only zoo.staff can read 'content'
    exports zoo.animal.talks.content to zoo.staff;

    // unqualified exports — anyone who requires this module sees them
    exports zoo.animal.talks.media;
    exports zoo.animal.talks.schedule;

    // open for reflection — visible at runtime via setAccessible(true)
    // even though it stays encapsulated at compile time
    opens zoo.animal.talks.schedule.internal;
}
