package pl.training.bank.domain.model;

import java.util.List;

public record Page<T>(List<T> items, long totalPages) {
}
