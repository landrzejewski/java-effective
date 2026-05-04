package pl.training.modules.M03_ModuleDeclaration.zoo.guest_bad.zoo.guest_bad;

// THIS IMPORT MUST FAIL: `zoo.animal.talks.content` is exported only to zoo.staff.
import pl.training.modules.M03_ModuleDeclaration.zoo.animal.talks.zoo.animal.talks.content.Script;

public class BadApp {
    public static void main(String... args) {
        System.out.println(Script.text()); // unreachable — compile fails before this runs
    }
}
