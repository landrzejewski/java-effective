package pl.training.bank.domain;

import pl.training.bank.domain.model.*;
import pl.training.bank.domain.model.Operation.AccountCreated;
import pl.training.bank.domain.model.Operation.Deposit;
import pl.training.bank.domain.model.Operation.Transfer;
import pl.training.bank.domain.model.Operation.Withdraw;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Currency;

public final class BankOperationsProxy implements BankApi {

    private final BankApi bankApi;
    private final Operations operations;
    private final Clock clock;

    public BankOperationsProxy(final BankApi bankApi, final Operations operations, final Clock clock) {
        this.bankApi = bankApi;
        this.operations = operations;
        this.clock = clock;
    }

    @Override
    public Account createAccount(final Currency currency, final boolean isPremium) {
        var account = bankApi.createAccount(currency, isPremium);
        var operation = new AccountCreated(account.getNumber(), LocalDateTime.now(clock));
        operations.record(operation);
        return account;
    }

    @Override
    public void deposit(final AccountNumber number, final Money amount) throws AccountNotFoundException {
        bankApi.deposit(number, amount);
        var operation = new Deposit(number, amount, LocalDateTime.now(clock));
        operations.record(operation);
    }

    @Override
    public void withdraw(final AccountNumber number, final Money amount) throws AccountNotFoundException, InsufficientFundsException {
        bankApi.withdraw(number, amount);
        var operation = new Withdraw(number, amount, LocalDateTime.now(clock));
        operations.record(operation);
    }

    @Override
    public void transfer(final AccountNumber fromNumber, final AccountNumber toNumber, final Money amount) throws AccountNotFoundException, InsufficientFundsException {
        bankApi.transfer(fromNumber, toNumber, amount);
        var operation = new Transfer(fromNumber, toNumber, amount, LocalDateTime.now(clock));
        operations.record(operation);
    }

}
