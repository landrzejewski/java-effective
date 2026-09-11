package pl.training.bank.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

public record Money(BigDecimal value, Currency currency) {

    public Money {
        /* getDefaultFractionDigits() returns -1 for pseudo-currencies (XDR, XAU, ...), and
           setScale(-1, ...) would silently round to the nearest ten: 119.50 -> 1.2E+2. */
        var fractionDigits = currency.getDefaultFractionDigits();
        if (fractionDigits < 0) {
            throw new IllegalArgumentException("Unsupported currency: " + currency.getCurrencyCode());
        }
        value = value.setScale(fractionDigits, RoundingMode.HALF_EVEN);
    }

    /* BigDecimal.valueOf, not new BigDecimal(double): the constructor captures the exact binary value,
       so new BigDecimal(2.675) is 2.67499999999999982... and rounds to 2.67, while
       BigDecimal.valueOf(2.675) is 2.675 and rounds to 2.68. */
    public static Money of(final double value, final Currency currency) {
        return new Money(BigDecimal.valueOf(value), currency);
    }

    public Money add(final Money money) {
        checkCurrencyCompatibility(money.currency);
        return new Money(value.add(money.value), currency);
    }

    public Money subtract(final Money money) {
        checkCurrencyCompatibility(money.currency);
        return new Money(value.subtract(money.value), currency);
    }

    public boolean isGreaterOrEqual(final Money money) {
        checkCurrencyCompatibility(money.currency);
        return value.compareTo(money.value) >= 0;
    }

    private void checkCurrencyCompatibility(final Currency currency) {
        if (!hasCurrency(currency)) {
            throw new IllegalArgumentException("Currencies are not compatible");
        }
    }

    public boolean hasCurrency(final Currency currency) {
       return this.currency == currency;
    }

}
