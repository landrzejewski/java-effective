package pl.training.bank.common.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.util.function.Supplier;

public final class HikariDataSourceSupplier implements Supplier<DataSource> {

    private static final int MAX_POOL_SIZE = 6;

    private final String url;
    private final String user;
    private final String password;

    public HikariDataSourceSupplier(final String url, final String user, final String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    @Override
    public DataSource get() {
        var config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        return new HikariDataSource(config);
    }

}
