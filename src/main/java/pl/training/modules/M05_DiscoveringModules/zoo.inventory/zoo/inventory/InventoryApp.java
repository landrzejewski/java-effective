package pl.training.modules.M05_DiscoveringModules.zoo.inventory.zoo.inventory;

import java.sql.DriverManager;        // pulls java.sql at compile and run time
import java.util.logging.Logger;      // pulls java.logging

public class InventoryApp {
    private static final Logger LOG = Logger.getLogger(InventoryApp.class.getName());

    public static void main(String... args) {
        LOG.info("inventory app started");
        // We do not actually load any driver — we just touch the API to
        // demonstrate that `requires java.sql` works.
        System.out.println("DriverManager loaded from " + DriverManager.class.getModule());
    }
}
