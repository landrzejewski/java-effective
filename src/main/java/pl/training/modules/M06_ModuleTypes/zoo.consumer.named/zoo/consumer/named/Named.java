package pl.training.modules.M06_ModuleTypes.zoo.consumer.named.zoo.consumer.named;

import pl.training.modules.M06_ModuleTypes.zoo.legacy.zoo.legacy.LegacyHelper;

public class Named {
    public static void main(String... args) {
        System.out.println(LegacyHelper.greet("named consumer"));
        System.out.println("  this class is in module: " + Named.class.getModule());
        System.out.println("  helper class is in module: " + LegacyHelper.class.getModule()
                + "    (automatic — derived from JAR filename)");
    }
}
