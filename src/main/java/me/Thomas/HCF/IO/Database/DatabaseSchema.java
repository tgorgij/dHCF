package me.Thomas.HCF.IO.Database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Versioned, transactional upgrades shared by fresh installs and existing databases. */
public final class DatabaseSchema {
    private static final int VERSION = 1;

    private DatabaseSchema() {}

    public static void initializeStorage(Connection connection) throws SQLException {
        // PRAGMA journal_mode returns a result set. Close it before starting a transaction.
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA journal_mode = WAL")) {
            while (result.next()) {
                // Consume the result so SQLite finishes the journal-mode statement.
            }
        }
        initialize(connection);
    }

    public static void initialize(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) throw new SQLException("Schema migration requires its own transaction");
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schemaMigrations (version INTEGER PRIMARY KEY, appliedAt INTEGER NOT NULL DEFAULT (unixepoch()))");
            int version;
            try (ResultSet rows = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schemaMigrations")) {
                rows.next();
                version = rows.getInt(1);
            }
            if (version > VERSION) throw new SQLException("Database schema is newer than this plugin supports: " + version);
            createBaseTables(statement);
            if (version < 1) {
                migrateInvites(statement);
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (1)");
            }
            connection.commit();
        } catch (SQLException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
                connection.close();
            }
            throw exception;
        } finally {
            if (!connection.isClosed()) connection.setAutoCommit(true);
        }
    }

    private static void createBaseTables(Statement statement) throws SQLException {
        String players = """
                CREATE TABLE IF NOT EXISTS players (
                    uuid TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    faction TEXT,
                    balance REAL NOT NULL DEFAULT 0.0,
                    kills INTEGER NOT NULL DEFAULT 0,
                    deaths INTEGER NOT NULL DEFAULT 0,
                    lives INTEGER NOT NULL DEFAULT 0,
                    timers TEXT NOT NULL DEFAULT '{}',
                    deathbanExpiry INTEGER NOT NULL DEFAULT 0
                )
                """;

        String factions = """
                CREATE TABLE IF NOT EXISTS factions (
                    uuid TEXT PRIMARY KEY,
                    type TEXT NOT NULL DEFAULT 'SYSTEM',
                    name TEXT NOT NULL,
                    leader TEXT,
                    balance REAL NOT NULL DEFAULT 0.0,
                    lives INTEGER NOT NULL DEFAULT 0,
                    members TEXT NOT NULL DEFAULT '{}',
                    color TEXT NOT NULL DEFAULT 'BLUE',
                    home TEXT,
                    open BOOLEAN NOT NULL DEFAULT FALSE,
                    dtr REAL NOT NULL DEFAULT 0.0,
                    points INTEGER NOT NULL DEFAULT 0,
                    kothCaptures INTEGER NOT NULL DEFAULT 0,
                    announcement TEXT,
                    deathban BOOLEAN DEFAULT TRUE
                )
                """;

        String factionInvites = """
        CREATE TABLE IF NOT EXISTS factionInvites (
            faction TEXT NOT NULL,
            user TEXT NOT NULL,
            time INTEGER NOT NULL DEFAULT (unixepoch()),
            PRIMARY KEY (faction, user)
        )
        """;

        String factionInvitesUserIndex = """
        CREATE INDEX IF NOT EXISTS idxInvitesUser
        ON factionInvites (user)
        """;

        String factionClaims = """
        CREATE TABLE IF NOT EXISTS factionClaims (
            claimID INTEGER PRIMARY KEY,
            faction TEXT NOT NULL,
            world TEXT NOT NULL,
            minX INTEGER NOT NULL,
            maxX INTEGER NOT NULL,
            minZ INTEGER NOT NULL,
            maxZ INTEGER NOT NULL,

            FOREIGN KEY (faction)
                REFERENCES factions(uuid)
                ON DELETE CASCADE,

            CHECK (minX <= maxX),
            CHECK (minZ <= maxZ)
        )
        """;

        String factionClaimsWorldIndex = """
        CREATE INDEX IF NOT EXISTS idxClaimsWorld
        ON factionClaims (world)
        """;

        String factionClaimsFactionIndex = """
        CREATE INDEX IF NOT EXISTS idxClaimsFaction
        ON factionClaims (faction)
        """;

            statement.executeUpdate(players);
            statement.executeUpdate(factions);
            statement.executeUpdate(factionInvites);
            statement.executeUpdate(factionInvitesUserIndex);
            statement.executeUpdate(factionClaims);
            statement.executeUpdate(factionClaimsWorldIndex);
            statement.executeUpdate(factionClaimsFactionIndex);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idxPlayersFaction ON players (faction)");
    }

    private static void migrateInvites(Statement statement) throws SQLException {
        // Keep broken legacy references available for inspection instead of silently discarding them.
        statement.executeUpdate("""
                CREATE TABLE archivedFactionInvites (
                    faction TEXT NOT NULL,
                    user TEXT NOT NULL,
                    time INTEGER NOT NULL,
                    reason TEXT NOT NULL,
                    archivedAt INTEGER NOT NULL DEFAULT (unixepoch()),
                    PRIMARY KEY (faction, user)
                ) WITHOUT ROWID
                """);
        statement.executeUpdate("""
                INSERT INTO archivedFactionInvites (faction, user, time, reason)
                SELECT i.faction, i.user, i.time,
                    CASE WHEN f.uuid IS NULL AND p.uuid IS NULL THEN 'missing faction and player'
                         WHEN f.uuid IS NULL THEN 'missing faction' ELSE 'missing player' END
                FROM factionInvites i
                LEFT JOIN factions f ON f.uuid = i.faction
                LEFT JOIN players p ON p.uuid = i.user
                WHERE f.uuid IS NULL OR p.uuid IS NULL
                """);
        statement.executeUpdate("""
                CREATE TABLE factionInvites_v1 (
                    faction TEXT NOT NULL REFERENCES factions(uuid) ON DELETE CASCADE,
                    user TEXT NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
                    time INTEGER NOT NULL DEFAULT (unixepoch()),
                    PRIMARY KEY (faction, user)
                ) WITHOUT ROWID
                """);
        statement.executeUpdate("""
                INSERT INTO factionInvites_v1 (faction, user, time)
                SELECT i.faction, i.user, i.time FROM factionInvites i
                JOIN factions f ON f.uuid = i.faction
                JOIN players p ON p.uuid = i.user
                """);
        statement.executeUpdate("DROP TABLE factionInvites");
        statement.executeUpdate("ALTER TABLE factionInvites_v1 RENAME TO factionInvites");
        statement.executeUpdate("CREATE INDEX idxInvitesUser ON factionInvites (user)");
    }
}
