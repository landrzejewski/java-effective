package pl.training.bank.domain;

import pl.training.bank.domain.model.Account;
import pl.training.bank.domain.model.AccountNumber;
import pl.training.bank.domain.model.InsufficientFundsException;
import pl.training.bank.domain.model.Money;

import java.util.Currency;

public interface BankApi {

    Account createAccount(Currency currency, boolean isPremium);

    void deposit(AccountNumber number, Money amount) throws AccountNotFoundException;

    void withdraw(AccountNumber number, Money amount) throws AccountNotFoundException, InsufficientFundsException;

    void transfer(AccountNumber fromNumber, AccountNumber toNumber, Money amount) throws AccountNotFoundException, InsufficientFundsException;

}
