package pl.training.bank.domain.reporting;

import pl.training.bank.domain.model.Account;
import pl.training.bank.domain.model.Formatter;
import pl.training.bank.domain.model.Money;
import pl.training.bank.domain.model.PremiumAccount;
import pl.training.bank.domain.AccountRepository;

import java.util.*;
import java.util.function.Predicate;

import static java.lang.System.lineSeparator;
import static java.math.BigDecimal.ZERO;
import static java.util.stream.Collectors.*;

public final class Reports {

    public static final class Predicates {

        private Predicates() {
        }

        public static Predicate<Account> all() {
            return account -> true;
        }

        public static Predicate<Account> premium() {
            return account -> account instanceof PremiumAccount;
        }

        public static Predicate<Account> inCurrency(final Currency currency) {
            return account -> currency ==  account.getCurrency();
        }

        public static Predicate<Account> balanceOver(final Money amount) {
            return inCurrency(amount.currency())
                    .and(account -> account.getBalance().isGreaterOrEqual(amount));
        }

    }

    private final AccountRepository accountRepository;

    public Reports(final AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Money totalBalance(final Currency currency) {
        return accountRepository.findAll()
                .map(Account::getBalance)
                .filter(money -> money.hasCurrency(currency))
                .reduce(new Money(ZERO, currency), Money::add);
    }

    public Map<Currency, Money> totalBalanceByCurrency() {
        return accountRepository.findAll()
                .collect(toMap(Account::getCurrency, Account::getBalance, Money::add));
    }

    public Map<Boolean, List<Account>> accountsByType() {
        return accountRepository.findAll()
                .collect(partitioningBy(account -> account instanceof PremiumAccount));
    }

    public Optional<Account> richestAccount(final Currency currency) {
        return accountRepository.findAll()
                .filter(account -> account.getCurrency() == currency)
                .max(Comparator.comparing(account -> account.getBalance().value()));
    }

    public DoubleSummaryStatistics balanceStatistics(final Currency currency) {
        return accountRepository.findAll()
                .map(Account::getBalance)
                .filter(money -> money.hasCurrency(currency))
                .mapToDouble(value -> value.value().doubleValue())
                .summaryStatistics();
    }

    public String custom(final Predicate<Account> predicate, final Formatter<Account> accountFormatter) {
        var body = accountRepository.findBy(predicate)
                .map(accountFormatter::format)
                .collect(joining(lineSeparator()));
        return """
                --- Report ---
                %s
                """.formatted(body);
    }

}

