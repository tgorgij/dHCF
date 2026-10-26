package me.Thomas.HCF.IO.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Persists unapplied combat-logger deaths; living reconnect snapshots stay in memory. */
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

    public void discardSnapshots() {
        database.save(List.of(new SaveSnapshot("DELETE FROM combatLoggerStates WHERE died = 0", () -> {}))).join();
    }

    public State get(UUID player) {
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT inventory, heldSlot, health, died, absorption, effects FROM combatLoggerStates WHERE player = ? AND died = 1")) {
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

    private void save(UUID player, State state) {
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
}
