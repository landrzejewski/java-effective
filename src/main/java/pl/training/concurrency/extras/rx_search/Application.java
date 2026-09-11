package pl.training.concurrency.extras.rx_search;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import pl.training.concurrency.extras.rx_search.github.GithubService;
import pl.training.concurrency.extras.rx_search.github.Repository;
import pl.training.concurrency.extras.rx_search.wikipedia.Article;
import pl.training.concurrency.extras.rx_search.wikipedia.WikipediaService;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Reads search terms from the console and queries GitHub and Wikipedia in parallel, merging both answers.
 *
 * <p>This is the reactive counterpart of the fan-out in Mod009 §8 (CompletableFuture) and Mod012 §2
 * (StructuredTaskScope): two independent I/O calls, one combined result, wall time equal to the slower of the two.
 * {@code zip} is the Rx equivalent of {@code thenCombine}, and {@code subscribeOn(Schedulers.io())} on each branch
 * is what makes them actually run concurrently.
 *
 * <p>Needs network access — it calls the public GitHub and Wikipedia APIs. Type a query and press Enter;
 * Ctrl-D ends the session.
 */
public class Application {

    private static final int DEBOUNCE_SECONDS = 1;

    private final GithubService githubService = new GithubService(retrofit("https://api.github.com/"));
    private final WikipediaService wikipediaService = new WikipediaService(retrofit("https://en.wikipedia.org/w/"));
    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    public static void main(String[] args) throws InterruptedException {
        new Application().start();
    }

    private Retrofit retrofit(String url) {
        return new Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(JacksonConverterFactory.create())
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                .client(new OkHttpClient.Builder()
                        .addInterceptor(new HttpLoggingInterceptor()
                                .setLevel(HttpLoggingInterceptor.Level.BASIC))
                        .build())
                .build();
    }

    private Observable<List<String>> githubTitles(String query) {
        return githubService.getRepositories(query)
                .flatMap(Observable::fromIterable)
                .map(Repository::name)
                .toList()                       // one allocation, not one per element
                .toObservable()
                .subscribeOn(Schedulers.io());
    }

    private Observable<List<String>> wikipediaTitles(String query) {
        return wikipediaService.getArticles(query)
                .flatMap(Observable::fromIterable)
                .map(Article::title)
                .toList()
                .toObservable()
                .subscribeOn(Schedulers.io());
    }

    /** Concatenates both result lists. The earlier version built a merged list and then returned the wrong one. */
    private List<String> merge(List<String> results, List<String> otherResults) {
        var merged = new ArrayList<String>(results.size() + otherResults.size());
        merged.addAll(results);
        merged.addAll(otherResults);
        return merged;
    }

    private void start() throws InterruptedException {
        Runtime.getRuntime().addShutdownHook(new Thread(compositeDisposable::dispose, "rx-dispose"));
        var finished = new CountDownLatch(1);

        System.out.println("Type a search term and press Enter (Ctrl-D to quit):");
        // The disposable IS registered this time — the shutdown hook used to dispose an empty composite.
        compositeDisposable.add(ObservableReader.from(System.in)
                .subscribeOn(Schedulers.io())
                .filter(query -> !query.isBlank())
                .debounce(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
                .switchMap(query -> Observable.zip(                    // switchMap: a new query cancels the old one
                        githubTitles(query), wikipediaTitles(query), this::merge))
                .subscribe(
                        results -> results.forEach(result -> System.out.println("  " + result)),
                        error -> {
                            System.out.println("failed: " + error);
                            finished.countDown();
                        },
                        finished::countDown));

        finished.await();
        compositeDisposable.dispose();
    }
}
