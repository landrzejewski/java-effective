package pl.training.bank.persistence;

import pl.training.bank.common.jdbc.JdbcTemplate;
import pl.training.bank.common.jdbc.ParameterSource;
import pl.training.bank.domain.model.*;
import pl.training.bank.domain.AccountRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class JdbcAccountRepository implements AccountRepository {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS accounts (
                id IDENTITY PRIMARY KEY,
                number VARCHAR(255) NOT NULL UNIQUE,
                balance DECIMAL(19, 4) NOT NULL,
                currency VARCHAR(3) NOT NULL,
                premium BOOLEAN NOT NULL
            )
            """;
    private static final String MERGE = "MERGE INTO accounts(number, balance, currency, premium) KEY(number) VALUES (?, ?, ?, ?)";
    private static final String FIND_BY_NUMBER = "SELECT * FROM accounts WHERE number = ?";
    private static final String FIND_ALL = "SELECT * FROM accounts";
    private static final String FIND_PAGE = "SELECT * FROM accounts ORDER BY id LIMIT ? OFFSET ?";
    private static final String COUNT = "SELECT COUNT(*) FROM accounts";

    private final JdbcTemplate jdbcTemplate;

    public JdbcAccountRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        execute(() -> {
            jdbcTemplate.createTable(CREATE_TABLE);
            return null;
        });
    }

    @Override
    public Account save(final Account account) {
        execute(() -> {
            jdbcTemplate.update(MERGE, statement -> {
                statement.setString(1, account.getNumber().number());
                statement.setBigDecimal(2, account.getBalance().value());
                statement.setString(3, account.getCurrency().getCurrencyCode());
                statement.setBoolean(4, account instanceof PremiumAccount);
            });
            return null;
        });
        return account;
    }

    @Override
    public Optional<Account> findByNumber(final AccountNumber number) {
        return execute(() -> jdbcTemplate.select(
                FIND_BY_NUMBER,
                statement -> statement.setString(1, number.number()),
                resultSet -> resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty()
        ));
    }

    @Override
    public Page<Account> findAll(final PageRequest pageRequest) {
        var items = execute(() -> jdbcTemplate.select(
                FIND_PAGE,
                statement -> {
                    statement.setInt(1, pageRequest.size());
                    statement.setInt(2, pageRequest.offset());
                },
                this::mapAll
        ));
        var totalElements = execute(() -> jdbcTemplate.select(
                COUNT,
                ParameterSource.NONE,
                resultSet -> resultSet.next() ? resultSet.getLong(1) : 0L
        ));
        var totalPages = pageRequest.size() == 0 ? 0 : (long) Math.ceil((double) totalElements / pageRequest.size());
        return new Page<>(items, totalPages);
    }

    @Override
    public Stream<Account> findAll() {
        return execute(() -> jdbcTemplate.select(FIND_ALL, ParameterSource.NONE, this::mapAll)).stream();
    }

    @Override
    public Stream<Account> findBy(final Predicate<Account> predicate) {
        return findAll().filter(predicate);
    }

    private List<Account> mapAll(final ResultSet resultSet) throws SQLException {
        var accounts = new ArrayList<Account>();
        while (resultSet.next()) {
            accounts.add(map(resultSet));
        }
        return accounts;
    }

    private Account map(final ResultSet resultSet) throws SQLException {
        var number = new AccountNumber(resultSet.getString("number"));
        var currency = Currency.getInstance(resultSet.getString("currency"));
        var balance = new Money(resultSet.getBigDecimal("balance"), currency);
        return resultSet.getBoolean("premium") ? new PremiumAccount(number, balance) : new Account(number, balance);
    }

    private <T> T execute(final JdbcCall<T> call) {
        try {
            return call.call();
        } catch (final SQLException exception) {
            throw new DataAccessException(exception);
        }
    }

    @FunctionalInterface
    private interface JdbcCall<T> {
        T call() throws SQLException;
    }

}
