package zoo.animal.talks.schedule.internal;

// This class is reached only via reflection because the package is `opens` (not exported).
public class Slot {
    private final String label;
    private Slot(String label) { this.label = label; }
    @Override public String toString() { return "Slot[" + label + "]"; }
}
