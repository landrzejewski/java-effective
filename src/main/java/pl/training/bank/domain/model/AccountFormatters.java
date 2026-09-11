package pl.training.bank.domain.model;

public final class AccountFormatters {

    private static final Formatter<Money> MONEY_FORMATTER = MoneyFormatters.defaultLocale();

    private AccountFormatters() {
    }

    public static Formatter<Account> simple() {
        return account -> account.getNumber().number() + ": " + MONEY_FORMATTER.format(account.getBalance());
    }

    public static Formatter<Account> detailed() {
        return account -> String.format("%s | %s | %s",  account.getNumber().number(), MONEY_FORMATTER.format(account.getBalance()), account.getCurrency());
    }

    public static Formatter<Account> csv() {
        return account -> String.join(", ", account.getNumber().number(), account.getBalance().value().toPlainString(), account.getCurrency().getCurrencyCode());
    }

}
