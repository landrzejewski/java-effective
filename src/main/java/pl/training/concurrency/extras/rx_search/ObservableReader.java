package pl.training.concurrency.extras.rx_search;

import io.reactivex.rxjava3.core.Observable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/** Blocking line reader exposed as an Observable. See chat_v3.ObservableReader for the same notes on disposal. */
public final class ObservableReader {

    private ObservableReader() {
    }

    static Observable<String> from(InputStream inputStream) {
        return Observable.create(emitter -> {
            var reader = new BufferedReader(new InputStreamReader(inputStream));
            emitter.setCancellable(inputStream::close);   // unblocks readLine() on disposal
            try {
                String text;
                while (!emitter.isDisposed() && (text = reader.readLine()) != null) {
                    emitter.onNext(text);
                }
                if (!emitter.isDisposed()) {
                    emitter.onComplete();
                }
            } catch (IOException exception) {
                if (!emitter.isDisposed()) {
                    emitter.onError(exception);
                }
            }
        });
    }
}
