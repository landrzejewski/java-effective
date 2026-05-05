package pl.training.concurrency.extras.rx_search.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Repository(String name, String description) {
}
