package me.Thomas.HCF.IO.Database;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Pending reconnect snapshots, stored separately from normal user saves. */
public final class CombatLoggerStateStore {
    public record State(byte[] inventory, int heldSlot, double health, boolean died, double absorption, String effects) {
        public State(byte[] inventory, int heldSlot, double health) {
            this(inventory, heldSlot, health, false, 0, "");
        }

        public State(byte[] inventory, int heldSlot, double health, boolean died) {
            this(inventory, heldSlot, health, died, 0, "");
        }

        public State {
            inventory = inventory.clone();
            effects = Objects.requireNonNull(effects);
            if (heldSlot < 0 || heldSlot > 8 || !Double.isFinite(health)
                    || (died ? health != 0 : health <= 0) || !Double.isFinite(absorption) || absorption < 0) {
                throw new IllegalArgumentException("Invalid combat-logger state");
            }
        }

        @Override
        public byte[] inventory() {
            return inventory.clone();
        }
    }

    private final DatabaseManager database;

    public CombatLoggerStateStore(DatabaseManager database) {
        this.database = database;
    }

    public Map<UUID, State> load() {
        Map<UUID, State> states = new HashMap<>();
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT player, inventory, heldSlot, health, died, absorption, effects FROM combatLoggerStates");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                states.put(UUID.fromString(rows.getString("player")),
                        readState(rows));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load combat-logger states", exception);
        }
        return states;
    }

    public State get(UUID player) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT inventory, heldSlot, health, died, absorption, effects FROM combatLoggerStates WHERE player = ?")) {
            statement.setString(1, player.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? readState(rows) : null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read combat-logger state", exception);
        }
    }

    private State readState(ResultSet rows) throws SQLException {
        return new State(rows.getBytes("inventory"), rows.getInt("heldSlot"), rows.getDouble("health"), rows.getBoolean("died"),
                rows.getDouble("absorption"), rows.getString("effects"));
    }

    public void markDead(UUID player) {
        save(player, new State(new byte[0], 0, 0, true));
    }

    public void save(UUID player, State state) {
        database.save(List.of(new SaveSnapshot("""
                INSERT INTO combatLoggerStates (player, inventory, heldSlot, health, died, absorption, effects) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(player) DO UPDATE SET inventory = excluded.inventory,
                    heldSlot = excluded.heldSlot, health = excluded.health, died = excluded.died,
                    absorption = excluded.absorption, effects = excluded.effects
                """, () -> {}, player.toString(), state.inventory(), state.heldSlot(), state.health(), state.died() ? 1 : 0,
                state.absorption(), state.effects()))).join();
    }

    public void delete(UUID player) {
        database.save(List.of(new SaveSnapshot("DELETE FROM combatLoggerStates WHERE player = ?",
                () -> {}, player.toString()))).join();
    }

    public void importLegacy(File file) {
        if (!file.isFile()) return;
        try {
            YamlConfiguration legacy = new YamlConfiguration();
            legacy.load(file);
            List<SaveSnapshot> snapshots = new ArrayList<>();
            for (String key : legacy.getKeys(false)) {
                UUID player = UUID.fromString(key);
                State state = new State(Base64.getDecoder().decode(Objects.requireNonNull(legacy.getString(key + ".inventory"))),
                        legacy.getInt(key + ".held-slot"), legacy.getDouble(key + ".health"));
                // Retrying an import must not replace a newer snapshot already in SQLite.
                snapshots.add(new SaveSnapshot("""
                        INSERT INTO combatLoggerStates (player, inventory, heldSlot, health) VALUES (?, ?, ?, ?)
                        ON CONFLICT(player) DO NOTHING
                        """, () -> {}, player.toString(), state.inventory(), state.heldSlot(), state.health()));
            }
            database.save(snapshots).join();
            Files.move(file.toPath(), file.toPath().resolveSibling(file.getName() + ".migrated"));
        } catch (Exception exception) {
            // Fail startup so an unfinished import is retried before snapshots can be consumed.
            throw new IllegalStateException("Could not migrate combat-logger YAML snapshots", exception);
        }
    }
}
