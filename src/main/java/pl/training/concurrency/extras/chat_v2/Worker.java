package pl.training.concurrency.extras.chat_v2;

import pl.training.concurrency.extras.chat_v2.commons.TextReader;
import pl.training.concurrency.extras.chat_v2.commons.TextWriter;

import java.net.Socket;

import static pl.training.concurrency.extras.chat_v2.ServerEventType.CONNECTION_CLOSED;
import static pl.training.concurrency.extras.chat_v2.ServerEventType.MESSAGE_RECEIVED;

class Worker implements Runnable {

    private final Socket socket;
    private final EventsBus eventsBus;
    private final TextWriter writer;

    Worker(Socket socket, EventsBus eventsBus) {
        this.socket = socket;
        this.eventsBus = eventsBus;
        writer = new TextWriter(socket);
    }

    @Override
    public void run() {
        new TextReader(socket, this::onText, this::onInputClose).read();
    }

    private void onText(String text) {
        eventsBus.publish(new ServerEvent(MESSAGE_RECEIVED, text, this));
    }

    private void onInputClose() {
        eventsBus.publish(new ServerEvent(CONNECTION_CLOSED, null, this));
    }

    void send(String text) {
        writer.write(text);
    }

}
