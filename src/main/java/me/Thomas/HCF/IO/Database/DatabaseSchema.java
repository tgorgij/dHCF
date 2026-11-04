package me.Thomas.HCF.IO.Database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Versioned, transactional upgrades shared by fresh installs and existing databases. */
public final class DatabaseSchema {
    private static final int VERSION = 23;

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
            if (version < 2) {
                statement.executeUpdate("""
                        CREATE TABLE combatLoggerStates (
                            player TEXT PRIMARY KEY REFERENCES players(uuid) ON DELETE CASCADE,
                            inventory BLOB NOT NULL,
                            heldSlot INTEGER NOT NULL CHECK (heldSlot BETWEEN 0 AND 8),
                            health REAL NOT NULL CHECK (health > 0)
                        ) WITHOUT ROWID
                        """);
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (2)");
            }
            if (version < 3) {
                statement.executeUpdate("""
                        CREATE TABLE combatLoggerStates_v3 (
                            player TEXT PRIMARY KEY REFERENCES players(uuid) ON DELETE CASCADE,
                            inventory BLOB NOT NULL,
                            heldSlot INTEGER NOT NULL CHECK (heldSlot BETWEEN 0 AND 8),
                            health REAL NOT NULL,
                            died INTEGER NOT NULL DEFAULT 0 CHECK (died IN (0, 1)),
                            CHECK ((died = 0 AND health > 0) OR (died = 1 AND health = 0))
                        ) WITHOUT ROWID
                        """);
                statement.executeUpdate("INSERT INTO combatLoggerStates_v3 (player, inventory, heldSlot, health) SELECT player, inventory, heldSlot, health FROM combatLoggerStates");
                statement.executeUpdate("DROP TABLE combatLoggerStates");
                statement.executeUpdate("ALTER TABLE combatLoggerStates_v3 RENAME TO combatLoggerStates");
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (3)");
            }
            if (version < 4) {
                statement.executeUpdate("ALTER TABLE factions ADD COLUMN dtrFreezeExpiry INTEGER NOT NULL DEFAULT 0");
                statement.executeUpdate("ALTER TABLE factions ADD COLUMN dtrNextRegen INTEGER NOT NULL DEFAULT 0");
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (4)");
            }
            if (version < 5) {
                statement.executeUpdate("ALTER TABLE combatLoggerStates ADD COLUMN absorption REAL NOT NULL DEFAULT 0");
                statement.executeUpdate("ALTER TABLE combatLoggerStates ADD COLUMN effects TEXT NOT NULL DEFAULT ''");
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (5)");
            }
            if (version < 6) {
                statement.executeUpdate("ALTER TABLE players ADD COLUMN pvpProtectionPending INTEGER NOT NULL DEFAULT 0");
                statement.executeUpdate("UPDATE players SET pvpProtectionPending = 1 WHERE deathbanExpiry <> 0");
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (6)");
            }
            if (version < 7) {
                statement.executeUpdate("ALTER TABLE players ADD COLUMN lastInventory BLOB");
                statement.executeUpdate("ALTER TABLE players ADD COLUMN lastExperience INTEGER");
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (7)");
            }
            if (version < 8) {
                statement.executeUpdate("""
                        CREATE TABLE koths (
                            uuid TEXT PRIMARY KEY,
                            name TEXT NOT NULL COLLATE NOCASE UNIQUE,
                            color TEXT NOT NULL DEFAULT 'BLUE',
                            pearlable INTEGER NOT NULL DEFAULT 1 CHECK (pearlable IN (0, 1)),
                            faction TEXT REFERENCES factions(uuid) ON DELETE SET NULL,
                            pos1 TEXT,
                            pos2 TEXT
                        )
                        """);
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (8)");
            }
            if (version < 9) {
                statement.executeUpdate("ALTER TABLE players ADD COLUMN mountainMessages INTEGER NOT NULL DEFAULT 1 CHECK (mountainMessages IN (0, 1))");
                statement.executeUpdate("""
                        CREATE TABLE mountains (
                            faction TEXT PRIMARY KEY REFERENCES factions(uuid) ON DELETE CASCADE,
                            intervalMinutes INTEGER NOT NULL DEFAULT 30 CHECK (intervalMinutes > 0),
                            block TEXT NOT NULL DEFAULT 'GLOWSTONE',
                            pos1 TEXT,
                            pos2 TEXT,
                            nextRegeneration INTEGER NOT NULL DEFAULT 0
                        )
                        """);
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (9)");
            }
            if (version < 10) {
                statement.executeUpdate("""
                        CREATE TABLE conquest (
                            id INTEGER PRIMARY KEY CHECK (id = 1),
                            faction TEXT NOT NULL UNIQUE REFERENCES factions(uuid) ON DELETE CASCADE
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE conquestZones (
                            conquest INTEGER NOT NULL DEFAULT 1 REFERENCES conquest(id) ON DELETE CASCADE CHECK (conquest = 1),
                            color TEXT NOT NULL CHECK (color IN ('RED', 'YELLOW', 'GREEN', 'BLUE')),
                            intervalSeconds INTEGER NOT NULL DEFAULT 30 CHECK (intervalSeconds > 0),
                            pos1 TEXT,
                            pos2 TEXT,
                            PRIMARY KEY (conquest, color)
                        ) WITHOUT ROWID
                        """);
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (10)");
            }
            if (version < 11) {
                statement.executeUpdate("""
                        CREATE TABLE conquestZones_v11 (
                            color TEXT PRIMARY KEY CHECK (color IN ('RED', 'YELLOW', 'GREEN', 'BLUE')),
                            intervalSeconds INTEGER NOT NULL DEFAULT 30 CHECK (intervalSeconds > 0),
                            pos1 TEXT,
                            pos2 TEXT
                        ) WITHOUT ROWID
                        """);
                statement.executeUpdate("INSERT INTO conquestZones_v11 (color, intervalSeconds, pos1, pos2) SELECT color, intervalSeconds, pos1, pos2 FROM conquestZones");
                statement.executeUpdate("DROP TABLE conquestZones");
                statement.executeUpdate("DROP TABLE conquest");
                statement.executeUpdate("ALTER TABLE conquestZones_v11 RENAME TO conquestZones");
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (11)");
            }
            if (version < 12) {
                statement.executeUpdate("ALTER TABLE players ADD COLUMN messages INTEGER NOT NULL DEFAULT 1 CHECK (messages IN (0, 1))");
                statement.executeUpdate("ALTER TABLE players ADD COLUMN messageSounds INTEGER NOT NULL DEFAULT 1 CHECK (messageSounds IN (0, 1))");
                statement.executeUpdate("ALTER TABLE players ADD COLUMN scoreboard INTEGER NOT NULL DEFAULT 1 CHECK (scoreboard IN (0, 1))");
                statement.executeUpdate("ALTER TABLE players ADD COLUMN publicChat INTEGER NOT NULL DEFAULT 1 CHECK (publicChat IN (0, 1))");
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (12)");
            }
            if (version < 13) {
                statement.executeUpdate("ALTER TABLE players ADD COLUMN requestReportCooldowns INTEGER NOT NULL DEFAULT 1 CHECK (requestReportCooldowns IN (0, 1))");
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (13)");
            }
            if (version < 14) {
                statement.executeUpdate("""
                        CREATE TABLE eventSchedules (
                            id INTEGER PRIMARY KEY,
                            type TEXT NOT NULL CHECK (type IN ('KOTH', 'CONQUEST')),
                            koth TEXT REFERENCES koths(uuid) ON DELETE CASCADE,
                            startTime TEXT NOT NULL,
                            durationSeconds INTEGER NOT NULL DEFAULT 0,
                            lastRun TEXT,
                            CHECK ((type = 'KOTH' AND koth IS NOT NULL AND durationSeconds > 0)
                                OR (type = 'CONQUEST' AND koth IS NULL AND durationSeconds = 0))
                        )
                        """);
                statement.executeUpdate("CREATE UNIQUE INDEX idxEventSchedule ON eventSchedules(type, COALESCE(koth, ''), startTime)");
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (14)");
            }
            if (version < 15) {
                statement.executeUpdate("ALTER TABLE factions ADD COLUMN kills INTEGER NOT NULL DEFAULT 0 CHECK (kills >= 0)");
                statement.executeUpdate("UPDATE factions SET kills = MIN(2147483647, MAX(0, COALESCE((SELECT SUM(kills) FROM players WHERE players.faction = factions.uuid), 0)))");
                statement.executeUpdate("""
                        CREATE TABLE factionTechUnlocks (
                            faction TEXT NOT NULL REFERENCES factions(uuid) ON DELETE CASCADE,
                            tree TEXT NOT NULL,
                            node TEXT NOT NULL,
                            PRIMARY KEY (faction, tree, node)
                        ) WITHOUT ROWID
                        """);
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (15)");
            }
            if (version < 16) {
                statement.executeUpdate("ALTER TABLE factions RENAME COLUMN kothCaptures TO eventCaptures");
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (16)");
            }
            if (version < 17) {
                statement.executeUpdate("""
                        CREATE TABLE dtc (
                            id INTEGER PRIMARY KEY CHECK (id = 1),
                            world TEXT,
                            x INTEGER NOT NULL DEFAULT 0,
                            y INTEGER NOT NULL DEFAULT 0,
                            z INTEGER NOT NULL DEFAULT 0,
                            blockData TEXT,
                            health INTEGER NOT NULL CHECK (health > 0)
                        )
                        """);
                statement.executeUpdate("""
                        CREATE TABLE eventSchedules_v17 (
                            id INTEGER PRIMARY KEY,
                            type TEXT NOT NULL CHECK (type IN ('KOTH', 'CONQUEST', 'DTC')),
                            koth TEXT REFERENCES koths(uuid) ON DELETE CASCADE,
                            startTime TEXT NOT NULL,
                            durationSeconds INTEGER NOT NULL DEFAULT 0,
                            lastRun TEXT,
                            CHECK ((type = 'KOTH' AND koth IS NOT NULL AND durationSeconds > 0)
                                OR (type IN ('CONQUEST', 'DTC') AND koth IS NULL AND durationSeconds = 0))
                        )
                        """);
                statement.executeUpdate("INSERT INTO eventSchedules_v17 SELECT * FROM eventSchedules");
                statement.executeUpdate("DROP TABLE eventSchedules");
                statement.executeUpdate("ALTER TABLE eventSchedules_v17 RENAME TO eventSchedules");
                statement.executeUpdate("CREATE UNIQUE INDEX idxEventSchedule ON eventSchedules(type, COALESCE(koth, ''), startTime)");
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (17)");
            }
            if (version < 18) {
                statement.executeUpdate("ALTER TABLE players ADD COLUMN reclaimed INTEGER NOT NULL DEFAULT 0 CHECK (reclaimed IN (0, 1))");
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (18)");
            }
            if (version < 19) {
                statement.executeUpdate("ALTER TABLE players ADD COLUMN abilityCooldowns INTEGER NOT NULL DEFAULT 1 CHECK (abilityCooldowns IN (0, 1))");
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (19)");
            }
            if (version < 20) {
                statement.executeUpdate("ALTER TABLE players ADD COLUMN filteredItems TEXT NOT NULL DEFAULT ''");
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (20)");
            }
            if (version < 21) {
                statement.executeUpdate("ALTER TABLE players ADD COLUMN ignoredPlayers TEXT NOT NULL DEFAULT ''");
                statement.executeUpdate("ALTER TABLE players ADD COLUMN notes TEXT NOT NULL DEFAULT ''");
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (21)");
            }
            if (version < 22) {
                statement.executeUpdate("ALTER TABLE factions ADD COLUMN allies TEXT NOT NULL DEFAULT ''");
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (22)");
            }
            if (version < 23) {
                statement.executeUpdate("ALTER TABLE players ADD COLUMN killstreak INTEGER NOT NULL DEFAULT 0");
                statement.executeUpdate("ALTER TABLE players ADD COLUMN highestKillstreak INTEGER NOT NULL DEFAULT 0");
                statement.executeUpdate("INSERT INTO schemaMigrations(version) VALUES (23)");
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
