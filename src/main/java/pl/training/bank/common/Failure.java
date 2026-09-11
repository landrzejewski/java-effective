package pl.training.bank.common;

public record Failure<E, V>(E error) implements Either<E, V> {}
