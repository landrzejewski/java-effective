package pl.training.bank.domain.model;

public final class PremiumAccount extends Account {

    public PremiumAccount(final AccountNumber number, final Money balance) {
        super(number, balance);
    }

    @Override
    public void withdraw(final Money amount) {
        balance = balance.subtract(amount);
    }

}
