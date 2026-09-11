package zoo.guest_bad;

// THIS IMPORT MUST FAIL: `zoo.animal.talks.content` is exported only to zoo.staff.
import zoo.animal.talks.content.Script;

public class BadApp {
    public static void main(String... args) {
        System.out.println(Script.text()); // unreachable — compile fails before this runs
    }
}
