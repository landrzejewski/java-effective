package pl.training.concurrency.exercises.mod009;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Exercise 9.1 — Order pipeline with thenCompose and thenCombine.
 *
 * <p>Related: Mod009 §2, §3, §4
 */
public final class Ex91OrderPipeline {

    private Ex91OrderPipeline() {}

    record User(long id, String name) {}
    record Cart(List<String> items) {}
    record Order(String user, long total, String shipping, String coupon) {}

    // Four fake services. Each sleeps, so each belongs on an I/O executor, never on the common pool.
    static User findUser(long id)                { sleep(60);  return new User(id, "alice"); }
    static Cart loadCart(User user)              { sleep(120); return new Cart(List.of("book", "mug")); }
    static long price(Cart cart)                 { sleep(40);  return cart.items().size() * 4900L; }
    static String loadShippingQuote(User user)   { sleep(90);  return "standard/4.90"; }
    static String loadCoupon(User user)          { sleep(70);  return "SPRING-10"; }

    public static void main(String[] args) {
        try (ExecutorService io = Executors.newVirtualThreadPerTaskExecutor()) {

            System.out.println("[1] thenApply with an async-returning function nests the future");
            // fetchCart returns a future, so thenApply wraps a future inside a future.
            CompletableFuture<CompletableFuture<Cart>> nested = CompletableFuture
                    .supplyAsync(() -> findUser(42), io)
                    .thenApply(user -> CompletableFuture.supplyAsync(() -> loadCart(user), io));
            System.out.println("  static type is CompletableFuture<CompletableFuture<Cart>>");
            System.out.println("  two joins needed: " + nested.join().join());

            System.out.println("[2] building the whole graph before joining anything");
            // Every stage is declared first. Nothing below blocks until [3], so the branches really do overlap
            // and the wall time in [5] is the longest PATH through this graph.
            long t0 = System.nanoTime();
            CompletableFuture<User> userF = CompletableFuture.supplyAsync(() -> findUser(42), io);

            // thenCompose flattens the future-returning call — the flat-map of the future world.
            CompletableFuture<Long> totalF = userF
                    .thenCompose(user -> CompletableFuture.supplyAsync(() -> loadCart(user), io))
                    .thenApply(Ex91OrderPipeline::price);       // a pure transformation -> thenApply is right here

            // Two more branches that only need the user, so they overlap with cart+price.
            CompletableFuture<String> shippingF = userF
                    .thenComposeAsync(user -> CompletableFuture.supplyAsync(() -> loadShippingQuote(user), io), io);
            CompletableFuture<String> couponF = userF
                    .thenComposeAsync(user -> CompletableFuture.supplyAsync(() -> loadCoupon(user), io), io);

            // thenCombine zips two independent branches into one.
            CompletableFuture<Order> orderF = totalF.thenCombine(shippingF,
                    (total, shipping) -> new Order("alice", total, shipping, null));
            System.out.println("  graph declared after " + millisSince(t0) + " ms — nothing has been awaited yet");

            System.out.println("[3] thenCompose result");
            System.out.println("  total = " + totalF.join() + " gr");

            System.out.println("[4] allOf — barrier over a heterogeneous fan-out");
            CompletableFuture.allOf(orderF, couponF).join();    // returns Void: read each value afterwards
            Order order = orderF.join();
            System.out.println("  " + new Order(order.user(), order.total(), order.shipping(), couponF.join()));

            long wallMillis = millisSince(t0);
            // Longest path: findUser(60) -> loadCart(120) -> price(40) = 220 ms.
            // The shipping branch (60+90) and the coupon branch (60+70) hide behind it entirely.
            // Running all five calls one after another would cost 380 ms.
            System.out.printf(Locale.ROOT,
                    "[5] wall time = %d ms ≈ longest path (60+120+40 = 220 ms), not the sum (380 ms)%n",
                    wallMillis);
        }

        // thenApply is map: it applies a plain function to the resolved value, so a function that itself
        // returns a future produces CompletableFuture<CompletableFuture<T>>. Every downstream stage would
        // then have to unwrap twice, and an exception inside the INNER future would not propagate to the
        // outer chain at all — exceptionally/handle attached to the outer future would never see it.
        // thenCompose is flatMap: it subscribes to the inner future and completes with its result, so the
        // chain stays one level deep and failures propagate normally.
        System.out.println("Ex91OrderPipeline finished");
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
