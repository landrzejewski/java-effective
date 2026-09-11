package pl.training.bank.domain;

import pl.training.bank.BankException;
import pl.training.bank.domain.model.AccountNumber;

public final class AccountNotFoundException extends BankException {

    private final AccountNumber accountNumber;

    public AccountNotFoundException(final AccountNumber accountNumber) {
        this.accountNumber = accountNumber;
    }

    public AccountNumber getAccountNumber() {
        return accountNumber;
    }

}
