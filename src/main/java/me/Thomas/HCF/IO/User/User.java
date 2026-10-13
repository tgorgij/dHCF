package me.Thomas.HCF.IO.User;

import me.Thomas.HCF.IO.Claims.Claim;
import me.Thomas.HCF.IO.Claims.Claiming;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import me.Thomas.HCF.IO.Database.SaveSnapshot;
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

    private long revision;
    private volatile long savedRevision;
    private Claiming claiming;
    private UUID lastEnteredFaction;
    private boolean bypass;
    private ArrayList<Claim> map;

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
     * Loads a user or initializes defaults, then updates the name.
     */
    public User(UUID uuid, String name) {
        this.uuid = uuid;

        load();

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

                readRow(resultSet);
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not load user data for " + uuid, exception);
        }
    }

    /** Hydrates an existing row without another query. */
    public User(ResultSet row) throws SQLException {
        this.uuid = UUID.fromString(row.getString("uuid"));
        readRow(row);
    }

    private void readRow(ResultSet resultSet) throws SQLException {
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


        this.savedRevision = revision;
        this.claiming = null;
        this.lastEnteredFaction = null;
        this.bypass = false;
        this.map = new ArrayList<>();
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
        this.revision++;
        this.claiming = null;
        this.lastEnteredFaction = null;
        this.bypass = false;
        this.map = new ArrayList<>();

    }

    public void save() {
        SaveSnapshot snapshot = snapshot();
        if (snapshot != null) Main.getInstance().getDatabaseManager().save(List.of(snapshot)).join();
    }

    /** Call on the server thread before submitting database work. */
    public SaveSnapshot snapshot() {
        if (!isModified()) return null;
        String sql =
                "INSERT INTO players "
                        + "(uuid, name, faction, balance, kills, deaths, lives, "
                        + "timers, deathbanExpiry) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, faction=excluded.faction, "
                        + "balance=excluded.balance, kills=excluded.kills, deaths=excluded.deaths, "
                        + "lives=excluded.lives, timers=excluded.timers, deathbanExpiry=excluded.deathbanExpiry";
        long version = revision;
        return new SaveSnapshot(sql, () -> savedRevision = version,
                uuid.toString(), name, faction == null ? null : faction.toString(),
                balance, kills, deaths, lives, serializeTimers(), deathbanExpiry);
    }

    public long getDeathbanExpiry() {
        return deathbanExpiry;
    }

    public void setDeathbanExpiry(long deathbanExpiry) {
        if (this.deathbanExpiry == deathbanExpiry) {
            return;
        }

        this.deathbanExpiry = deathbanExpiry;
        revision++;
    }

    private String serializeTimers() {
        StringBuilder result = new StringBuilder();

        for (Map.Entry<Timer.Type, Timer> entry : timers.entrySet()) {
            if (entry.getKey() == Timer.Type.HOME) {
                continue;
            }

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
                if (type == Timer.Type.HOME) {
                    continue;
                }

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
        revision++;
    }

    public UUID getFaction() {
        return faction;
    }

    public void setFaction(UUID faction) {
        if (Objects.equals(faction, this.faction)) {
            return;
        }

        if (claiming != null && !claiming.isClaimingFor() && !Objects.equals(claiming.getFaction(), faction)) {
            setClaiming(null);
        }
        this.faction = faction;
        revision++;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        if (Double.compare(this.balance, balance) == 0) {
            return;
        }

        this.balance = balance;
        revision++;
    }

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        if (this.kills == kills) {
            return;
        }

        this.kills = kills;
        revision++;
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        if (this.deaths == deaths) {
            return;
        }

        this.deaths = deaths;
        revision++;
    }

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {
        if (this.lives == lives) {
            return;
        }

        this.lives = lives;
        revision++;
    }

    public boolean isModified() {
        return revision != savedRevision;
    }

    public void addTimer(Timer.Type type, Timer timer) {
        timers.put(type, timer);
        revision++;
    }

    public boolean hasTimer(Timer.Type type) {
        Timer timer = timers.get(type);
        return timer != null && timer.getRemainingTimeMillis() > 0;
    }

    public Timer getTimer(Timer.Type type) {
        return timers.get(type);
    }

    public void removeTimer(Timer.Type type) {
        if (timers.remove(type) != null) {
            revision++;
        }
    }

    public void expireTimers() {
        Iterator<Map.Entry<Timer.Type, Timer>> iterator = timers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Timer.Type, Timer> entry = iterator.next();
            if (entry.getKey() == Timer.Type.HOME || entry.getValue().getRemainingTimeMillis() > 0) continue;
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(me.Thomas.HCF.Utils.Chat.C.chat(
                        Main.getInstance().getLocaleConfig().getString("events.player.timer.end.default", "")
                                .replace("%timer%", entry.getKey().name().toLowerCase(Locale.ROOT))));
            }
            iterator.remove();
            revision++;
        }
    }

    public void pauseTimers() {
        for (Timer timer : timers.values()) {
            if (timer.isPauseOnLogout() && !timer.isPaused()) {
                timer.pause();
                revision++;
            }
        }
    }

    public void unpauseTimers() {
        for (Timer timer : timers.values()) {
            if (timer.isPauseOnLogout() && timer.isPaused()) {
                timer.pause();
                revision++;
            }
        }
    }

    public boolean hasFaction() {
        return faction != null;
    }

    public void saveAsync() {
        SaveSnapshot snapshot = snapshot();
        if (snapshot != null) Main.getInstance().getDatabaseManager().save(List.of(snapshot));
    }

    public Claiming getClaiming() {
        return claiming;
    }

    public void setClaiming(Claiming claiming) {
        if (this.claiming != null && !this.claiming.isClaimingFor()) {
            me.Thomas.HCF.IO.Faction.Faction oldFaction = Main.getInstance().getFaction(this.claiming.getFaction());
            if (oldFaction != null) oldFaction.setClaiming(false);
        }
        this.claiming = claiming;
        if (claiming != null && !claiming.isClaimingFor()) {
            me.Thomas.HCF.IO.Faction.Faction newFaction = Main.getInstance().getFaction(claiming.getFaction());
            if (newFaction != null) newFaction.setClaiming(true);
        }
    }

    public UUID getLastEnteredFaction() {
        return lastEnteredFaction;
    }

    public void setLastEnteredFaction(UUID lastEnteredFaction) {
        this.lastEnteredFaction = lastEnteredFaction;
    }

    public boolean isBypass() {
        return bypass;
    }

    public void setBypass(boolean bypass) {
        this.bypass = bypass;
    }

    public ArrayList<Claim> getMap() {
        return map;
    }
}
