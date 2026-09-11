package pl.training.concurrency.extras.rx_search.wikipedia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Query(List<Article> search) {
}
