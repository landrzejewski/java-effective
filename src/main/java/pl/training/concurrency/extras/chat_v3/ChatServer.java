package pl.training.concurrency.extras.chat_v3;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The reactive variant of the chat server (compare chat_v1: a thread per connection, chat_v2: a pool plus an
 * event bus).
 *
 * <p>Threading notes, since this is what the module is about:
 * <ul>
 *   <li>{@code Observable.create} is SYNCHRONOUS on subscribe. Without {@code subscribeOn} the accept loop would
 *       run on whatever thread called {@code start()} — here, main, which would block main for the lifetime of
 *       the server. The {@code subscribeOn(Schedulers.io())} below moves it off main.</li>
 *   <li>Moving it off main has a consequence that is easy to miss: {@code subscribe()} returns immediately, and
 *       EVERY RxJava scheduler thread is a daemon thread. If {@code main} simply returned here, the JVM would
 *       shut down before a single connection was accepted. That is why {@code start()} awaits a latch — once the
 *       source runs on a scheduler, the caller must keep a non-daemon thread alive.</li>
 *   <li>Each connection gets its own reader stream, also on the IO scheduler — an elastic pool of platform
 *       threads. On Java 21+ a virtual-thread scheduler is the better fit for exactly this shape of work
 *       (Mod011).</li>
 * </ul>
 */
public class ChatServer {

    private static final Logger log = Logger.getLogger(ChatServer.class.getName());
    private static final int DEFAULT_PORT = 8888;

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();
    private final Connections connections = new Connections();

    public static void main(String[] args) {
        new ChatServer().start(port(args));
    }

    private static int port(String[] args) {
        if (args.length == 0) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            log.warning("Unparseable port '" + args[0] + "', falling back to " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    private void start(int port) {
        Runtime.getRuntime().addShutdownHook(new Thread(compositeDisposable::dispose, "rx-dispose"));

        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException exception) {
            log.log(Level.SEVERE, "Server failed to start: " + exception.getMessage());
            return;
        }
        log.log(Level.INFO, "Server is listening on port: {0}", port);

        // The accept loop runs on an IO-scheduler thread, which is a daemon, so main has to wait here.
        var finished = new CountDownLatch(1);
        compositeDisposable.add(ObservableSocket.from(serverSocket)
                .subscribeOn(Schedulers.io())
                .subscribe(this::onNextSocket,
                        error -> {
                            log.log(Level.SEVERE, "Accept loop failed", error);
                            finished.countDown();
                        },
                        finished::countDown));
        try {
            finished.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void onNextSocket(Socket socket) throws IOException {
        var connection = new Connection(socket);
        connections.add(connection);
        log.log(Level.INFO, "New connection, {0} client(s) online", connections.size());
        compositeDisposable.add(createTextStream(socket)
                .subscribe(connections::broadcast,
                        error -> {
                            log.log(Level.WARNING, "Client stream failed", error);
                            connections.remove(connection);
                            connection.close();
                        },
                        () -> {
                            connections.remove(connection);
                            connection.close();
                        }));
    }

    private Observable<String> createTextStream(Socket socket) throws IOException {
        return ObservableReader.from(socket)
                .subscribeOn(Schedulers.io());
    }
}
