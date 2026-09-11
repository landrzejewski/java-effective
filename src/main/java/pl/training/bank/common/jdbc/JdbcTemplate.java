package pl.training.bank.common.jdbc;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.function.Supplier;

public final class JdbcTemplate {

    private static final int ID_COLUMN_INDEX = 1;

    private final DataSource dataSource;

    public JdbcTemplate(final Supplier<DataSource> dataSourceProvider) {
        this.dataSource = dataSourceProvider.get();
    }

    public void createTable(final String sql) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    public Optional<Long> insert(final String sql, final ParameterSource parameterSource) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            parameterSource.substitute(statement);
            statement.executeUpdate();
            var resultSet = statement.getGeneratedKeys();
            if (resultSet.next()) {
                var id = resultSet.getLong(ID_COLUMN_INDEX);
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    public <T> T select(final String sql, final ParameterSource parameterSource, final Mapper<T> mapper) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            parameterSource.substitute(statement);
            var resultSet = statement.executeQuery();
            return mapper.map(resultSet);
        }
    }

    public void update(final String sql, final ParameterSource parameterSource) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            parameterSource.substitute(statement);
            statement.executeUpdate();
        }
    }

}
