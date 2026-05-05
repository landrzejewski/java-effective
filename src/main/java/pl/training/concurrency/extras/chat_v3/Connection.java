package pl.training.concurrency.extras.chat_v3;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Connection {

    private static final Logger log = Logger.getLogger(Connection.class.getName());

    private PrintWriter writer;

    Connection(Socket socket) {
        try {
            writer = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException ex) {
            log.log(Level.INFO,  "Creating output stream failed - " + ex.getMessage());
        }
    }

    void send(String message) {
        writer.println(message);
    }

}
