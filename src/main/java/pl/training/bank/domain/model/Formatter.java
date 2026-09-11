package pl.training.bank.domain.model;

import java.util.function.UnaryOperator;

@FunctionalInterface
public interface Formatter<T> {

    String format(T value);

    default Formatter<T> andThen(UnaryOperator<String> after) {
        return value -> after.apply(format(value));
    }

}
