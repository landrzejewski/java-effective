package pl.training.modules.M06_ModuleTypes.zoo.legacy.zoo.legacy;

// Plain class in a plain JAR — this directory has NO module-info.java on purpose.
public class LegacyHelper {
    public static String greet(String who) {
        return "[legacy] hello, " + who;
    }
}
