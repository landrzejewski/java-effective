package pl.training.concurrency.exercises.mod002;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Exercise 2.3 — Immutable config snapshot.
 *
 * <p>Related: Mod002 §4, §8
 */
public final class Ex23ConfigHolder {

    private Ex23ConfigHolder() {}

    /** Immutable: record components are final and the list is defensively copied into an unmodifiable one. */
    record Config(String host, int port, List<String> features) {
        Config {
            features = List.copyOf(features);
        }

        Config withFeature(String feature) {
            var copy = new ArrayList<>(features);
            copy.add(feature);
            return new Config(host, port, copy);
        }
    }

    static final class ConfigHolder {
        private final AtomicReference<Config> current;

        ConfigHolder(Config initial) {
            current = new AtomicReference<>(initial);
        }

        Config current() {
            return current.get();
        }

        /** updateAndGet re-applies the function until its CAS succeeds — the function must be pure and cheap. */
        Config update(UnaryOperator<Config> change) {
            return current.updateAndGet(change);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        var holder = new ConfigHolder(new Config("localhost", 8080, List.of()));
        final int updaters = 4;
        final int updatesEach = 1_000;

        var updaterThreads = new ArrayList<Thread>();
        for (int u = 1; u <= updaters; u++) {
            String prefix = "f" + u + "-";
            updaterThreads.add(new Thread(() -> {
                for (int i = 0; i < updatesEach; i++) {
                    String feature = prefix + i;
                    holder.update(config -> config.withFeature(feature));
                }
            }, "updater-" + u));
        }

        var stop = new AtomicBoolean();
        var tornSeen = new AtomicBoolean();
        var readerThreads = new ArrayList<Thread>();
        for (int r = 1; r <= 4; r++) {
            readerThreads.add(new Thread(() -> {
                while (!stop.get()) {
                    var config = holder.current();
                    // A partially constructed Config would show default values (null / 0) here.
                    if (config.host() == null || config.port() != 8080 || config.features() == null) {
                        tornSeen.set(true);
                    }
                }
            }, "reader-" + r));
        }

        readerThreads.forEach(Thread::start);
        updaterThreads.forEach(Thread::start);
        for (var thread : updaterThreads) {
            thread.join();
        }
        stop.set(true);
        for (var thread : readerThreads) {
            thread.join();
        }

        var features = holder.current().features();
        System.out.printf("features: %d (expected %d)%n", features.size(), updaters * updatesEach);
        System.out.println("torn config observed: " + tornSeen.get());

        // Fully constructed: the final-field freeze (JLS 17.5) guarantees that any thread which sees a reference
        // to an object published after its constructor finished also sees its final fields correctly initialised.
        // Records make every component final, so a reader can never observe a half-built Config.
        // Latest reference: AtomicReference.get/set (and a successful CAS) have volatile semantics, so the write
        // of a new Config happens-before every later get() that returns it — readers see the newest snapshot.
        System.out.println("Ex23ConfigHolder finished");
    }
}
