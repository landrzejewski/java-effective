package pl.training.bank.domain;

import pl.training.bank.domain.model.Account;
import pl.training.bank.domain.model.AccountNumber;
import pl.training.bank.domain.model.Page;
import pl.training.bank.domain.model.PageRequest;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findByNumber(AccountNumber number);

    Page<Account> findAll(PageRequest pageRequest);

    Stream<Account> findAll();

    Stream<Account> findBy(Predicate<Account> predicate);

}
