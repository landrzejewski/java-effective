package pl.training.concurrency.extras.chat_v2.commons;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class TextReader {

    private static final Logger log = Logger.getLogger(TextReader.class.getName());

    private final Consumer<String> textConsumer;
    private BufferedReader reader;
    private Runnable onClose;

    public TextReader(InputStream inputStream, Consumer<String> textConsumer) {
        this.textConsumer = textConsumer;
        reader = new BufferedReader(new InputStreamReader(inputStream));
    }

    public TextReader(Socket socket, Consumer<String> textConsumer, Runnable onClose) {
        this.textConsumer = textConsumer;
        this.onClose = onClose;
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            log.severe("Creating input stream failed: " + e.getMessage());
        }
    }

    public void read() {
        String text;
        try {
            while ((text = reader.readLine()) != null) {
                textConsumer.accept(text);
            }
        } catch (IOException exception) {
            log.severe("Read message failed: " + exception.getMessage());
        } finally {
            if (onClose != null) {
                onClose.run();
            }
        }
    }

}
