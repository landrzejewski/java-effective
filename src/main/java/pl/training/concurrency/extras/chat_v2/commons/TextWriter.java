package pl.training.concurrency.extras.chat_v2.commons;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Logger;

public class TextWriter {

    private static final Logger log = Logger.getLogger(TextWriter.class.getName());

    private PrintWriter writer;

    public TextWriter(Socket socket) {
        try {
            writer = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException exception) {
           log.severe("Creating output stream failed: " + exception.getMessage());
        }
    }

    public void write(String text) {
        writer.println(text);
    }

}
