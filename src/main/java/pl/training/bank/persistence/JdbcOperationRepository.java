package pl.training.bank.persistence;

import pl.training.bank.common.jdbc.JdbcTemplate;
import pl.training.bank.common.jdbc.ParameterSource;
import pl.training.bank.domain.OperationRepository;
import pl.training.bank.domain.model.AccountNumber;
import pl.training.bank.domain.model.Money;
import pl.training.bank.domain.model.Operation;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

public final class JdbcOperationRepository implements OperationRepository {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS operations (
                id IDENTITY PRIMARY KEY,
                type VARCHAR(20) NOT NULL,
                number VARCHAR(255) NOT NULL,
                to_number VARCHAR(255),
                amount DECIMAL(19, 4),
                currency VARCHAR(3),
                timestamp TIMESTAMP NOT NULL
            )
            """;
    private static final String INSERT = "INSERT INTO operations(type, number, to_number, amount, currency, timestamp) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String FIND_ALL = "SELECT * FROM operations ORDER BY id";

    private static final String ACCOUNT_CREATED = "ACCOUNT_CREATED";
    private static final String DEPOSIT = "DEPOSIT";
    private static final String WITHDRAW = "WITHDRAW";
    private static final String TRANSFER = "TRANSFER";

    private final JdbcTemplate jdbcTemplate;

    public JdbcOperationRepository(final JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        execute(() -> {
            jdbcTemplate.createTable(CREATE_TABLE);
            return null;
        });
    }

    @Override
    public void save(final Operation operation) {
        execute(() -> jdbcTemplate.insert(INSERT, statement -> bind(statement, operation)));
    }

    @Override
    public List<Operation> findAll() {
        return execute(() -> jdbcTemplate.select(FIND_ALL, ParameterSource.NONE, this::mapAll));
    }

    private void bind(final PreparedStatement statement, final Operation operation) throws SQLException {
        statement.setString(2, operation.number().number());
        statement.setTimestamp(6, Timestamp.valueOf(operation.timestamp()));
        switch (operation) {
            case Operation.AccountCreated ignored -> {
                statement.setString(1, ACCOUNT_CREATED);
                statement.setString(3, null);
                statement.setNull(4, Types.DECIMAL);
                statement.setString(5, null);
            }
            case Operation.Deposit deposit -> bindAmount(statement, DEPOSIT, null, deposit.amount());
            case Operation.Withdraw withdraw -> bindAmount(statement, WITHDRAW, null, withdraw.amount());
            case Operation.Transfer transfer -> bindAmount(statement, TRANSFER, transfer.toNumber(), transfer.amount());
        }
    }

    private void bindAmount(final PreparedStatement statement, final String type, final AccountNumber toNumber, final Money amount) throws SQLException {
        statement.setString(1, type);
        statement.setString(3, toNumber == null ? null : toNumber.number());
        statement.setBigDecimal(4, amount.value());
        statement.setString(5, amount.currency().getCurrencyCode());
    }

    private List<Operation> mapAll(final ResultSet resultSet) throws SQLException {
        var operations = new ArrayList<Operation>();
        while (resultSet.next()) {
            operations.add(map(resultSet));
        }
        return operations;
    }

    private Operation map(final ResultSet resultSet) throws SQLException {
        var number = new AccountNumber(resultSet.getString("number"));
        var timestamp = resultSet.getTimestamp("timestamp").toLocalDateTime();
        return switch (resultSet.getString("type")) {
            case ACCOUNT_CREATED -> new Operation.AccountCreated(number, timestamp);
            case DEPOSIT -> new Operation.Deposit(number, money(resultSet), timestamp);
            case WITHDRAW -> new Operation.Withdraw(number, money(resultSet), timestamp);
            case TRANSFER -> new Operation.Transfer(number, new AccountNumber(resultSet.getString("to_number")), money(resultSet), timestamp);
            default -> throw new DataAccessException(new SQLException("Unknown operation type: " + resultSet.getString("type")));
        };
    }

    private Money money(final ResultSet resultSet) throws SQLException {
        var currency = Currency.getInstance(resultSet.getString("currency"));
        return new Money(resultSet.getBigDecimal("amount"), currency);
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
