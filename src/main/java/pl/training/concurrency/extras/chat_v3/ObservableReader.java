package pl.training.concurrency.extras.chat_v3;

import io.reactivex.rxjava3.core.Observable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;

/**
 * Turns a blocking line-oriented {@link InputStream} into an {@link Observable}.
 *
 * <p>Two things a naive Observable.create gets wrong, both fixed here:
 * <ul>
 *   <li><b>Disposal.</b> {@code readLine()} blocks, so disposing the subscription cannot interrupt it. Closing the
 *       underlying stream can — hence {@code setCancellable}. Without it every disposed subscription leaks a
 *       thread parked forever on a read.</li>
 *   <li><b>Protocol.</b> onComplete must not follow onError, and nothing may be emitted after disposal. The Rx
 *       contract is onNext* then at most one of onComplete / onError.</li>
 * </ul>
 */
public final class ObservableReader {

    private ObservableReader() {
    }

    static Observable<String> from(InputStream inputStream) {
        return Observable.create(emitter -> {
            var reader = new BufferedReader(new InputStreamReader(inputStream));
            // Disposal closes the stream, which makes the blocking readLine() below return or throw.
            emitter.setCancellable(inputStream::close);
            try {
                String message;
                while (!emitter.isDisposed() && (message = reader.readLine()) != null) {
                    emitter.onNext(message);
                }
                if (!emitter.isDisposed()) {
                    emitter.onComplete();
                }
            } catch (IOException exception) {
                if (!emitter.isDisposed()) {
                    emitter.onError(exception);     // terminal: no onComplete afterwards
                }
            }
        });
    }

    static Observable<String> from(Socket socket) throws IOException {
        return from(socket.getInputStream());
    }
}
