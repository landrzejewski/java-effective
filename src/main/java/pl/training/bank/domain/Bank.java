package pl.training.bank.domain;

import pl.training.bank.domain.model.*;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.function.Supplier;

public final class Bank implements BankApi {

    private final Supplier<AccountNumber> accountNumberSupplier;
    private final AccountRepository accountRepository;

    public Bank(final Supplier<AccountNumber> accountNumberSupplier, final AccountRepository accountRepository) {
        this.accountNumberSupplier = accountNumberSupplier;
        this.accountRepository = accountRepository;
    }

    @Override
    public Account createAccount(final Currency currency, final boolean isPremium) {
        var number = accountNumberSupplier.get();
        var balance = new Money(BigDecimal.ZERO, currency);
        var account = isPremium ? new PremiumAccount(number, balance) : new Account(number, balance);
        return accountRepository.save(account);
    }

    @Override
    public void deposit(final AccountNumber number, final Money amount) throws AccountNotFoundException {
        var account = accountRepository.findByNumber(number)
                .orElseThrow(() -> new AccountNotFoundException(number));
        account.deposit(amount);
        accountRepository.save(account);
    }

    @Override
    public void withdraw(final AccountNumber number, final Money amount) throws AccountNotFoundException, InsufficientFundsException {
        var account = accountRepository.findByNumber(number)
                .orElseThrow(() -> new AccountNotFoundException(number));
        account.withdraw(amount);
        accountRepository.save(account);
    }

    @Override
    public void transfer(final AccountNumber fromNumber, final AccountNumber toNumber, final Money amount) throws AccountNotFoundException, InsufficientFundsException {
        withdraw(fromNumber, amount);
        try {
            deposit(toNumber, amount);
        } catch (AccountNotFoundException accountNotFoundException) {
            deposit(fromNumber, amount);
            throw new AccountNotFoundException(toNumber);
        }
    }

}
