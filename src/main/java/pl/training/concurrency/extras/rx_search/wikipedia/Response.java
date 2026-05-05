package pl.training.concurrency.extras.rx_search.wikipedia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Response(Query query) {
}
