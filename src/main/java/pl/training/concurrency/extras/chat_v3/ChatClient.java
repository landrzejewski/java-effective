package pl.training.concurrency.extras.chat_v3;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

/**
 * subscribeOn vs observeOn — the two operators people mix up most often:
 *
 * <ul>
 *   <li>{@code subscribeOn} decides which thread the SOURCE is subscribed on, and therefore affects everything
 *       UPSTREAM of it. Only the first subscribeOn in a chain has any effect.</li>
 *   <li>{@code observeOn} switches the thread for everything DOWNSTREAM of it, and may appear many times.</li>
 *   <li>With only a subscribeOn, the whole chain runs on that thread. With only an observeOn, everything above it
 *       runs on the subscribing thread and only the part below it moves.</li>
 *   <li>Both subscriptions below run on scheduler threads, and every RxJava scheduler thread is a daemon. So
 *       {@code start()} must await a latch: without it {@code main} would return and the JVM would exit before
 *       anything could be typed.</li>
 * </ul>
 *
 * <p>Background: https://proandroiddev.com/understanding-rxjava-subscribeon-and-observeon-744b0c6a41ea
 */
public class ChatClient {

    private static final Logger log = Logger.getLogger(ChatClient.class.getName());
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8888;

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        String user = args.length > 2 ? args[2] : "user-" + ProcessHandle.current().pid();
        new ChatClient().start(host, port, user);
    }

    private void start(String host, int port, String user) throws IOException {
        Runtime.getRuntime().addShutdownHook(new Thread(compositeDisposable::dispose, "rx-dispose"));

        var socket = new Socket(host, port);
        var connection = new Connection(socket);

        // The session ends when either side of the conversation ends: end-of-input on the console, or the
        // server closing the socket. Either one releases main.
        var finished = new CountDownLatch(1);

        // Console input: read on one thread, write to the socket on another.
        compositeDisposable.add(ObservableReader.from(System.in)
                .subscribeOn(Schedulers.io())
                .map(message -> user + ": " + message)
                .observeOn(Schedulers.single())
                .subscribe(connection::send,
                        error -> {
                            log.warning("Console stream failed: " + error.getMessage());
                            finished.countDown();
                        },
                        finished::countDown));

        // Incoming messages, on their own IO thread.
        compositeDisposable.add(ObservableReader.from(socket)
                .subscribeOn(Schedulers.io())
                .subscribe(System.out::println,
                        error -> {
                            log.warning("Server stream failed: " + error.getMessage());
                            finished.countDown();
                        },
                        () -> {
                            System.out.println("disconnected");
                            finished.countDown();
                        }));

        try {
            finished.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            connection.close();
        }
    }
}
