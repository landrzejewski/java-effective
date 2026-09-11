package pl.training.bank.common;

public record Success<E, V>(V value) implements Either<E, V> {}
