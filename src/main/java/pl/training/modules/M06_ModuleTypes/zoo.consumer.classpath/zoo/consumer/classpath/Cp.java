package pl.training.modules.M06_ModuleTypes.zoo.consumer.classpath.zoo.consumer.classpath;

import pl.training.modules.M06_ModuleTypes.zoo.legacy.zoo.legacy.LegacyHelper;

// No module-info.java in this directory — at runtime this code lives in the unnamed module.
public class Cp {
    public static void main(String... args) {
        System.out.println(LegacyHelper.greet("classpath consumer"));
        System.out.println("  this class is in module: " + Cp.class.getModule()
                + "  (unnamed)");
        System.out.println("  helper class is in module: " + LegacyHelper.class.getModule());
    }
}
