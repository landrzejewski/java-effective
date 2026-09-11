package pl.training.bank;

import pl.training.bank.common.jdbc.HikariDataSourceSupplier;
import pl.training.bank.common.jdbc.JdbcTemplate;
import pl.training.bank.domain.*;
import pl.training.bank.domain.model.AccountNumber;
import pl.training.bank.domain.reporting.Reports;
import pl.training.bank.persistence.HashMapAccountRepository;
import pl.training.bank.persistence.JdbcAccountRepository;
import pl.training.bank.persistence.JdbcOperationRepository;
import pl.training.bank.persistence.ListOperationRepository;

import java.time.Clock;
import java.time.ZoneId;
import java.util.function.Supplier;

public final class BankConfiguration {

    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(new HikariDataSourceSupplier("jdbc:h2:file:./data/bank;AUTO_SERVER=TRUE", "sa", ""));
    private final AccountRepository accountRepository = new JdbcAccountRepository(jdbcTemplate);
    private final Operations operations = new Operations(new JdbcOperationRepository(jdbcTemplate));

    private Supplier<AccountNumber> accountNumberSupplier() {
        return new UUIDAccountNumberSupplier();
    }

    public Reports reports() {
        return new Reports(accountRepository);
    }

    public Operations operations() {
        return operations;
    }

    public AccountRepository accountRepository() {
        return accountRepository;
    }

    public BankApi bank() {
        var clock = Clock.system(ZoneId.of("Europe/Warsaw"));
        return new BankOperationsProxy(new Bank(accountNumberSupplier(), accountRepository), operations, clock);
    }

}
