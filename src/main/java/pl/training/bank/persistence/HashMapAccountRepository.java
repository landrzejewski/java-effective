package pl.training.bank.persistence;

import pl.training.bank.domain.model.Account;
import pl.training.bank.domain.model.AccountNumber;
import pl.training.bank.domain.model.Page;
import pl.training.bank.domain.model.PageRequest;
import pl.training.bank.domain.AccountRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class HashMapAccountRepository implements AccountRepository {

    private final Map<AccountNumber, Account> accounts = new LinkedHashMap<>();

    @Override
    public Account save(final Account account) {
        accounts.put(account.getNumber(), account);
        return account;
    }

    @Override
    public Optional<Account> findByNumber(final AccountNumber number) {
        return Optional.ofNullable(accounts.get(number));
    }

    @Override
    public Page<Account> findAll(final PageRequest pageRequest) {
        /* Paging needs a stable order, otherwise consecutive pages can overlap or skip entries.
           The JDBC sibling gets this from ORDER BY id; here it comes from sorting on the number. */
        var items = accounts.values().stream()
                .sorted(Comparator.comparing(account -> account.getNumber().number()))
                .skip(pageRequest.offset())
                .limit(pageRequest.size())
                .toList();
        var totalPages = pageRequest.size() == 0 ? 0 : (long) Math.ceil((double) accounts.size() / pageRequest.size());
        return new Page<>(items, totalPages);
    }

    @Override
    public Stream<Account> findAll() {
        return accounts.values().stream();
    }

    @Override
    public Stream<Account> findBy(final Predicate<Account> predicate) {
        return findAll().filter(predicate);
    }


}
