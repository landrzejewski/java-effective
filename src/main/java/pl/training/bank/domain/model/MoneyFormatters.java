package pl.training.bank.domain.model;

import java.text.NumberFormat;
import java.util.Locale;

public final class MoneyFormatters {

    private static final String SEPARATOR = " ";

    private MoneyFormatters() {
    }

    public static Formatter<Money> plain() {
        return money -> money.value().toPlainString() + SEPARATOR + money.currency().getCurrencyCode();
    }

    /* A fresh NumberFormat per call. NumberFormat is not thread-safe, and the returned Formatter is
       typically held in a static field (see AccountFormatters), so a captured-and-mutated instance
       would let two threads formatting different currencies observe each other's setCurrency. */
    public static Formatter<Money> local(final Locale locale) {
        return money -> {
            var formatter = NumberFormat.getCurrencyInstance(locale);
            formatter.setCurrency(money.currency());
            return formatter.format(money.value());
        };
    }

    public static Formatter<Money> defaultLocale() {
        return local(Locale.getDefault());
    }

}
