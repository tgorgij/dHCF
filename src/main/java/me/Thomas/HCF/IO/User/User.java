package me.Thomas.HCF.IO.User;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

public class User {

    private final UUID uuid;

    private String name;
    private UUID faction;
    private double balance;
    private int kills;
    private int deaths;
    private int lives;
    private long deathbanExpiry;

    private boolean modified;

    private Map<Timer.Type, Timer> timers =
            new EnumMap<>(Timer.Type.class);

    /**
     * Loads a user from the database.
     */
    public User(UUID uuid) {
        this.uuid = uuid;
        load();
    }

    /**
     * Creates or updates a user and saves the name immediately.
     */
    public User(UUID uuid, String name) {
        this.uuid = uuid;

        if (exists(uuid)) {
            load();
        } else {
            initializeDefaults();
        }

        setName(name);
    }

    public static User get(Player player) {
        return Main.getInstance().getUser(player.getUniqueId());
    }

    public static User get(UUID uuid) {
        return Main.getInstance().getUser(uuid);
    }

    public static User get(String name) {
        return Main.getInstance().getUser(name.toUpperCase(Locale.ROOT));
    }

    public void load() {
        String sql = "SELECT * FROM players WHERE uuid = ?";

        try (
                Connection connection =
                        Main.getInstance()
                                .getDatabaseManager()
                                .getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(1, uuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    initializeDefaults();
                    return;
                }

                this.name = resultSet.getString("name");

                String factionValue = resultSet.getString("faction");

                this.faction = factionValue == null || factionValue.isEmpty()
                        ? null
                        : UUID.fromString(factionValue);

                this.balance = resultSet.getDouble("balance");
                this.kills = resultSet.getInt("kills");
                this.deaths = resultSet.getInt("deaths");
                this.lives = resultSet.getInt("lives");

                this.timers = deserialize(
                        resultSet.getString("timers")
                );

                this.deathbanExpiry = resultSet.getLong("deathbanExpiry");


                this.modified = false;
            }
        } catch (SQLException | IllegalArgumentException exception) {
            Main.getInstance().getLogger().log(
                    Level.SEVERE,
                    "Could not load user data for " + uuid,
                    exception
            );
        }
    }

    private void initializeDefaults() {
        this.name = "";
        this.faction = null;
        this.balance = Main.getInstance().getConfig().getDouble("economy.default-balance");
        this.kills = 0;
        this.deaths = 0;
        this.lives = 0;
        this.timers = new EnumMap<>(Timer.Type.class);
        this.deathbanExpiry = 0;
        this.modified = true;

        saveAsync();
    }

    public void save() {
        if (!modified) {
            return;
        }

        String sql =
                "INSERT OR REPLACE INTO players "
                        + "(uuid, name, faction, balance, kills, deaths, lives, "
                        + "timers, deathbanExpiry) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String timersValue = serializeTimers();

        try (
                Connection connection =
                        Main.getInstance()
                                .getDatabaseManager()
                                .getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(1, uuid.toString());
            statement.setString(2, name);

            if (faction == null) {
                statement.setNull(3, java.sql.Types.VARCHAR);
            } else {
                statement.setString(3, faction.toString());
            }

            statement.setDouble(4, balance);
            statement.setInt(5, kills);
            statement.setInt(6, deaths);
            statement.setInt(7, lives);
            statement.setString(8, timersValue);
            statement.setLong(9, deathbanExpiry);

            statement.executeUpdate();

            modified = false;
        } catch (SQLException exception) {
            Main.getInstance().getLogger().log(
                    Level.SEVERE,
                    "Could not save user data for " + uuid,
                    exception
            );
        }
    }

    public long getDeathbanExpiry() {
        return deathbanExpiry;
    }

    public void setDeathbanExpiry(long deathbanExpiry) {
        if (this.deathbanExpiry == deathbanExpiry) {
            return;
        }

        this.deathbanExpiry = deathbanExpiry;
        modified = true;
    }

    private String serializeTimers() {
        StringBuilder result = new StringBuilder();

        for (Map.Entry<Timer.Type, Timer> entry : timers.entrySet()) {
            Timer timer = entry.getValue();

            if (!result.isEmpty()) {
                result.append(';');
            }

            result.append(entry.getKey().name())
                    .append('|')
                    .append(timer.getExpires())
                    .append('|')
                    .append(timer.getPausedTime())
                    .append('|')
                    .append(timer.isPauseOnLogout());
        }

        return result.toString();
    }

    private Map<Timer.Type, Timer> deserialize(String serialized) {
        Map<Timer.Type, Timer> result =
                new EnumMap<>(Timer.Type.class);

        if (serialized == null || serialized.isEmpty()) {
            return result;
        }

        for (String timerData : serialized.split(";")) {
            if (timerData.isEmpty()) {
                continue;
            }

            String[] values = timerData.split("\\|");

            if (values.length != 4) {
                continue;
            }

            try {
                Timer.Type type = Timer.Type.valueOf(values[0]);
                long expires = Long.parseLong(values[1]);
                long pausedTime = Long.parseLong(values[2]);
                boolean pauseOnLogout = Boolean.parseBoolean(values[3]);

                result.put(
                        type,
                        new Timer(
                                type,
                                expires,
                                pausedTime,
                                pauseOnLogout
                        )
                );
            } catch (IllegalArgumentException exception) {
                Main.getInstance().getLogger().log(
                        Level.WARNING,
                        "Ignoring malformed timer data: " + timerData,
                        exception
                );
            }
        }

        return result;
    }

    public static boolean exists(UUID uuid) {
        String sql = "SELECT 1 FROM players WHERE uuid = ?";

        try (
                Connection connection =
                        Main.getInstance()
                                .getDatabaseManager()
                                .getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(1, uuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            Main.getInstance().getLogger().log(
                    Level.SEVERE,
                    "Could not check if user exists for " + uuid,
                    exception
            );

            return false;
        }
    }

    public UUID getUUID() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (Objects.equals(name, this.name)) {
            return;
        }

        this.name = name;
        modified = true;
    }

    public UUID getFaction() {
        return faction;
    }

    public void setFaction(UUID faction) {
        if (Objects.equals(faction, this.faction)) {
            return;
        }

        this.faction = faction;
        modified = true;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        if (Double.compare(this.balance, balance) == 0) {
            return;
        }

        this.balance = balance;
        modified = true;
    }

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        if (this.kills == kills) {
            return;
        }

        this.kills = kills;
        modified = true;
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        if (this.deaths == deaths) {
            return;
        }

        this.deaths = deaths;
        modified = true;
    }

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {
        if (this.lives == lives) {
            return;
        }

        this.lives = lives;
        modified = true;
    }

    public boolean isModified() {
        return modified;
    }

    public void addTimer(Timer.Type type, Timer timer) {
        timers.put(type, timer);
        modified = true;
    }

    public boolean hasTimer(Timer.Type type) {
        return timers.containsKey(type);
    }

    public Timer getTimer(Timer.Type type) {
        return timers.get(type);
    }

    public void removeTimer(Timer.Type type) {
        if (timers.remove(type) != null) {
            modified = true;
        }
    }

    public void pauseTimers() {
        for (Timer timer : timers.values()) {
            if (timer.isPauseOnLogout() && !timer.isPaused()) {
                timer.pause();
                modified = true;
            }
        }
    }

    public void unpauseTimers() {
        for (Timer timer : timers.values()) {
            if (timer.isPauseOnLogout() && timer.isPaused()) {
                timer.pause();
                modified = true;
            }
        }
    }

    public boolean hasFaction() {
        return faction != null;
    }

    public void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(
                Main.getInstance(),
                this::save
        );
    }
}