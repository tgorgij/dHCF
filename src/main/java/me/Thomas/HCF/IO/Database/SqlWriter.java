package me.Thomas.HCF.IO.Database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Owns one connection, used exclusively by the ordered database worker. */
public final class SqlWriter implements AutoCloseable {
    @FunctionalInterface
    public interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    private final ConnectionFactory factory;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "dHCF-database");
        thread.setDaemon(true);
        return thread;
    });
    private Connection connection;

    public SqlWriter(ConnectionFactory factory) {
        this.factory = factory;
    }

    public CompletableFuture<Void> save(List<SaveSnapshot> snapshots) {
        List<SaveSnapshot> batch = List.copyOf(snapshots);
        if (batch.isEmpty()) return CompletableFuture.completedFuture(null);
        return CompletableFuture.runAsync(() -> {
            try {
                if (connection == null || connection.isClosed()) connection = factory.open();
                connection.setAutoCommit(false);
                try {
                    for (SaveSnapshot snapshot : batch) snapshot.execute(connection);
                    connection.commit();
                } catch (SQLException exception) {
                    try {
                        connection.rollback();
                    } catch (SQLException rollbackFailure) {
                        exception.addSuppressed(rollbackFailure);
                        try {
                            connection.close();
                        } catch (SQLException closeFailure) {
                            exception.addSuppressed(closeFailure);
                        }
                        connection = null;
                    }
                    throw exception;
                } finally {
                    if (connection != null) connection.setAutoCommit(true);
                }
                for (SaveSnapshot snapshot : batch) snapshot.acknowledge();
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
    }

    @Override
    public void close() {
        CompletableFuture<Void> closed = CompletableFuture.runAsync(() -> {
            try {
                if (connection != null) connection.close();
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, executor);
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Database writer did not finish within 30 seconds");
            }
            closed.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while draining database writes", exception);
        }
    }
}
