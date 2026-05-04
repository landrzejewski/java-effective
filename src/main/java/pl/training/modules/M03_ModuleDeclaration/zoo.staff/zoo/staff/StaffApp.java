package pl.training.modules.M03_ModuleDeclaration.zoo.staff.zoo.staff;

import pl.training.modules.M03_ModuleDeclaration.zoo.animal.talks.zoo.animal.talks.content.Script;          // OK: qualified-export targets zoo.staff
import pl.training.modules.M03_ModuleDeclaration.zoo.animal.talks.zoo.animal.talks.media.Recording;
import pl.training.modules.M03_ModuleDeclaration.zoo.animal.talks.zoo.animal.talks.schedule.Schedule;

public class StaffApp {
    public static void main(String... args) throws Exception {
        System.out.println("[staff] script: " + Script.text());
        System.out.println("[staff] recording: " + new Recording("daily-briefing", 120));
        System.out.println("[staff] schedule: " + Schedule.firstSlot());

        // Reach into the `opens` package via reflection, which is normally encapsulated.
        var slotClass = Class.forName("pl.training.modules.M03_ModuleDeclaration.zoo.animal.talks.zoo.animal.talks.schedule.internal.Slot");
        var ctor = slotClass.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);                // permitted because the package is `opens`
        Object slot = ctor.newInstance("morning");
        System.out.println("[staff] reflective: " + slot);
    }
}
