package pl.training.modules.M03_ModuleDeclaration.zoo.guest.zoo.guest;

import pl.training.modules.M03_ModuleDeclaration.zoo.animal.talks.zoo.animal.talks.media.Recording;        // OK: unqualified export
import pl.training.modules.M03_ModuleDeclaration.zoo.animal.talks.zoo.animal.talks.schedule.Schedule;      // OK: unqualified export

public class GuestApp {
    public static void main(String... args) {
        // We DO NOT 'requires zoo.animal.talks' directly — we get it via
        // 'requires transitive' from zoo.staff.
        System.out.println("[guest] " + new Recording("welcome-video", 30));
        System.out.println("[guest] " + Schedule.firstSlot());
    }
}
