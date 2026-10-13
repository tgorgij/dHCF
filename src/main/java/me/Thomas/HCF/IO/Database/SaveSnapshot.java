package me.Thomas.HCF.IO.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** SQL parameters captured on the server thread; acknowledgment runs only after commit. */
public final class SaveSnapshot {
    private final String sql;
    private final Object[] parameters;
    private final Runnable committed;

    public SaveSnapshot(String sql, Runnable committed, Object... parameters) {
        this.sql = sql;
        this.parameters = parameters.clone();
        this.committed = committed;
    }

    public void execute(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.executeUpdate();
        }
    }

    public void acknowledge() {
        committed.run();
    }
}
