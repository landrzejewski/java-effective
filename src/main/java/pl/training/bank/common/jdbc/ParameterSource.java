package pl.training.bank.common.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public interface ParameterSource {

    ParameterSource NONE = statement -> {
    };

    void substitute(PreparedStatement statement) throws SQLException;

}
