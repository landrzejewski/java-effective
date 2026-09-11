package pl.training.concurrency.extras.rx_search.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QueryResult(List<Repository> items) {
}
