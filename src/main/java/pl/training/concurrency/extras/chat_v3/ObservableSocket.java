package pl.training.concurrency.extras.chat_v3;

import io.reactivex.rxjava3.core.Observable;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/** Turns {@link ServerSocket#accept()} into a stream of incoming connections. */
public final class ObservableSocket {

    private ObservableSocket() {
    }

    static Observable<Socket> from(ServerSocket serverSocket) {
        return Observable.create(emitter -> {
            // accept() blocks; closing the server socket is the only way to unblock it on disposal.
            emitter.setCancellable(serverSocket::close);
            try {
                while (!emitter.isDisposed()) {
                    Socket socket = serverSocket.accept();
                    emitter.onNext(socket);
                }
                emitter.onComplete();
            } catch (IOException exception) {
                if (!emitter.isDisposed()) {
                    emitter.onError(exception);
                }
            }
        });
    }
}
