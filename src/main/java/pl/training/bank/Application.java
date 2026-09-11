package pl.training.bank;

import pl.training.bank.domain.model.InsufficientFundsException;
import pl.training.bank.domain.model.Money;
import pl.training.bank.domain.model.PageRequest;
import pl.training.bank.domain.AccountNotFoundException;

import java.time.LocalDate;
import java.util.Currency;

import static pl.training.bank.domain.model.AccountFormatters.csv;
import static pl.training.bank.domain.reporting.Reports.Predicates.all;
import static pl.training.bank.domain.reporting.Reports.Predicates.inCurrency;

public final class Application {

    private final static Currency DEFAULT_CURRENCY =  Currency.getInstance("PLN");

    static void main() throws AccountNotFoundException, InsufficientFundsException {
        var bankConfiguration = new BankConfiguration();
        var bank = bankConfiguration.bank();
        var reports = bankConfiguration.reports();
        var operations = bankConfiguration.operations();
        var repository = bankConfiguration.accountRepository();

        var firstAccount = bank.createAccount(DEFAULT_CURRENCY, false);
        var secondAccount = bank.createAccount(DEFAULT_CURRENCY, true);
        bank.deposit(firstAccount.getNumber(), Money.of(100, DEFAULT_CURRENCY));
        bank.withdraw(firstAccount.getNumber(), Money.of(50, DEFAULT_CURRENCY));
        bank.transfer(firstAccount.getNumber(), secondAccount.getNumber(), Money.of(50, DEFAULT_CURRENCY));

        System.out.println("Total balance: " + reports.totalBalance(DEFAULT_CURRENCY));
        System.out.println("Total balance by currency: " + reports.totalBalanceByCurrency());
        System.out.println("Accounts by type: " + reports.accountsByType());
        System.out.println("Richest account: " + reports.richestAccount(DEFAULT_CURRENCY));
        System.out.println("Balance statistics: " + reports.balanceStatistics(DEFAULT_CURRENCY));

        var today = LocalDate.now();
        System.out.println("All operations: " + operations.all());
        System.out.println("Operations for first account: " + operations.forAccount(firstAccount.getNumber()));
        System.out.println("Operations grouped by day: " + operations.groupByDay());
        System.out.println("Total deposited (first account): " + operations.totalDeposited(firstAccount.getNumber(), DEFAULT_CURRENCY));
        System.out.println("Account age (first account): " + operations.accountAge(firstAccount.getNumber(), today));
        System.out.println("Activity span (first account): " + operations.activitySpan(firstAccount.getNumber()));
        System.out.println("Statement (first account, today): " + operations.statement(firstAccount.getNumber(), today, today));

        System.out.println("Page 0 (size 1): " + repository.findAll(new PageRequest(0, 1)));
        System.out.println("Page 1 (size 1): " + repository.findAll(new PageRequest(1, 1)));

        var report = reports.custom(
                all().and(inCurrency(DEFAULT_CURRENCY)),
                csv().andThen(String::toUpperCase)
        );
        System.out.println(report);
    }

}
