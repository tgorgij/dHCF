package me.Thomas.HCF.IO.Database;

import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;

public class DatabaseManager {
    private final JavaPlugin plugin;
    private final File databaseFile;
    private final SqlWriter writer;

    public DatabaseManager(JavaPlugin plugin, String dbName) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), dbName + ".db");
        this.writer = new SqlWriter(this::getConnection);
    }

    public void load() {
        try {
            Files.createDirectories(databaseFile.toPath().getParent());
            Class.forName("org.sqlite.JDBC");
        } catch (IOException | ClassNotFoundException exception) {
            throw new IllegalStateException("Could not initialize SQLite", exception);
        }
        try (Connection connection = getConnection()) {
            DatabaseSchema.initializeStorage(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not initialize SQLite schema", exception);
        }
    }

    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + databaseFile
        );

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }

        return connection;
    }
    public CompletableFuture<Void> save(List<SaveSnapshot> snapshots) {
        CompletableFuture<Void> result = writer.save(snapshots);
        result.whenComplete((ignored, failure) -> {
            if (failure != null) plugin.getLogger().log(Level.SEVERE, "Could not persist database batch", failure);
        });
        return result;
    }

    public void close() {
        writer.close();
    }
}
