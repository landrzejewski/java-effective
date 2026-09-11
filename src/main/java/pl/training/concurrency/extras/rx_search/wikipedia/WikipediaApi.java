package pl.training.concurrency.extras.rx_search.wikipedia;

import io.reactivex.rxjava3.core.Observable;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WikipediaApi {

    @GET("api.php?action=query&format=json&list=search")
    Observable<Response> getArticles(@Query("srsearch") String query);

}
