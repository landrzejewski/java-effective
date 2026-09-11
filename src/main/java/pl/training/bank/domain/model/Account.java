package pl.training.bank.domain.model;

import java.util.Currency;
import java.util.Objects;

public sealed class Account permits PremiumAccount {

    private final AccountNumber number;
    protected Money balance;

    public Account(final AccountNumber number, final Money balance) {
        this.number = number;
        this.balance = balance;
    }

    public void deposit(final Money amount) {
        balance = balance.add(amount);
    }

    public void withdraw(final Money amount) throws InsufficientFundsException {
        checkBalance(amount);
        balance = balance.subtract(amount);
    }

    private void checkBalance(final Money amount) throws InsufficientFundsException {
        if (!balance.isGreaterOrEqual(amount)) {
            throw new InsufficientFundsException();
        }
    }

    public AccountNumber getNumber() {
        return number;
    }

    public Money getBalance() {
        return balance;
    }

    public Currency getCurrency() {
        return balance.currency();
    }

    /* Identity is the account number alone. `balance` is mutable (deposit/withdraw reassign it), so
       including it here would make an Account unfindable in a HashSet or HashMap after the first
       deposit - the classic "mutable key" bug. */
    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return Objects.equals(number, account.number);
    }

    @Override
    public int hashCode() {
        return Objects.hash(number);
    }

    @Override
    public String toString() {
        return "Account{" +
                "number=" + number +
                ", balance=" + balance +
                '}';
    }

}
