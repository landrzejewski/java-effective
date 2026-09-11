package pl.training.concurrency.extras.chat_v3;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.Socket;

/**
 * The writable end of one client connection.
 *
 * <p>The constructor now FAILS if the output stream cannot be opened. The earlier version logged the exception and
 * left {@code writer} null, so the object looked usable and then threw NullPointerException on first use — inside
 * a read lock held by {@link Connections#broadcast(String)}, which wedged the whole server. Never hand back a
 * half-constructed object.
 */
public class Connection {

    private final Socket socket;
    private final PrintWriter writer;

    Connection(Socket socket) {
        this.socket = socket;
        try {
            this.writer = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException exception) {
            throw new UncheckedIOException("Creating output stream failed", exception);
        }
    }

    void send(String message) {
        writer.println(message);
    }

    boolean isBroken() {
        return writer.checkError() || socket.isClosed();
    }

    void close() {
        writer.close();
        try {
            socket.close();
        } catch (IOException ignored) {
            // closing is best-effort
        }
    }
}
