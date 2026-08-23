package me.Thomas.HCF.Main;

import me.Thomas.HCF.Commands.User.Economy.BalanceCommand;
import me.Thomas.HCF.Commands.User.Faction.FactionCommand;
import me.Thomas.HCF.Events.Player.Connection.UserJoinEvent;
import me.Thomas.HCF.Events.Player.Connection.UserQuitEvent;
import me.Thomas.HCF.Events.Player.Item.UserPearlEvent;
import me.Thomas.HCF.IO.Database.DatabaseManager;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Utils.Scoreboard.BoardHandler;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.*;

public class Main extends JavaPlugin {

    public static Main instance;
    private DatabaseManager databaseManager;
    private FileConfiguration localeConfig;
    private FileConfiguration scoreboardConfig;
    private FileConfiguration messagesConfig;
    private BoardHandler boardHandler;

    // Users
    private final HashMap<UUID, User> users = new HashMap<>();
    private final HashMap<String, User> userNames = new HashMap<>();

    // Factions
    private final HashMap<UUID, Faction> factions = new HashMap<>();
    private final HashMap<String, Faction> factionNames = new HashMap<>();

    public Main() {
        instance = this;
    }

    public static Main getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        databaseManager = new DatabaseManager(this, "storage");
        databaseManager.load();

        files();
        loadLocale();
        loadScoreboard();
        loadMessages();
        boardHandler = new BoardHandler();

        loadData();

        getLogger().info("dHCF has been enabled with SQLite support!");
        events();
        commands();
        checkUsers();
        checkFactions();
    }

    @Override
    public void onDisable() {
        for (User user : users.values()) {
            user.save();
        }
        for (Faction faction : factions.values()) {
            faction.save();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("dHCF has been disabled.");
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public FileConfiguration getLocaleConfig() {
        return localeConfig;
    }

    public FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public FileConfiguration getScoreboardConfig() {
        return scoreboardConfig;
    }

    private void loadData() {
        String usersSql = "SELECT * FROM players";
        String factionsSql = "SELECT * FROM factions";

        String dbUrl = "jdbc:sqlite:plugins/dHCF/storage.db";

        // 1. Process Users independently
        try (
                Connection conn1 = DriverManager.getConnection(dbUrl);
                PreparedStatement usersStatement = conn1.prepareStatement(usersSql);
                ResultSet usersResultSet = usersStatement.executeQuery()
        ) {
            while (usersResultSet.next()) {
                User user = new User(UUID.fromString(usersResultSet.getString("uuid")));
                users.put(user.getUUID(), user);
                userNames.put(user.getName().toUpperCase(Locale.ROOT), user);
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }

        // 2. Process Factions independently
        try (
                Connection conn2 = DriverManager.getConnection(dbUrl);
                PreparedStatement factionsStatement = conn2.prepareStatement(factionsSql);
                ResultSet factionsResultSet = factionsStatement.executeQuery()
        ) {
            while (factionsResultSet.next()) {
                Faction faction = new Faction(UUID.fromString(factionsResultSet.getString("uuid")));
                factions.put(faction.getUUID(), faction);
                factionNames.put(faction.getName().toUpperCase(Locale.ROOT), faction);
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }



    private void files() {
        saveResource("config.yml", false);
        saveResource("locale.yml", false);
        saveResource("scoreboard.yml", false);
        saveResource("messages.yml", false);
    }

    private void loadLocale() {
        java.io.File localeFile = new java.io.File(getDataFolder(), "locale.yml");
        localeConfig = YamlConfiguration.loadConfiguration(localeFile);
    }

    private void loadScoreboard() {
        java.io.File scoreboardFile = new java.io.File(getDataFolder(), "scoreboard.yml");
        scoreboardConfig = YamlConfiguration.loadConfiguration(scoreboardFile);
    }

    private void loadMessages() {
        java.io.File messagesFile = new java.io.File(getDataFolder(), "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void commands() {

        // User
        Objects.requireNonNull(getCommand("faction")).setExecutor(new FactionCommand()); Objects.requireNonNull(getCommand("faction")).setTabCompleter(new FactionCommand());
        Objects.requireNonNull(getCommand("balance")).setExecutor(new BalanceCommand()); Objects.requireNonNull(getCommand("balance")).setTabCompleter(new BalanceCommand());
    }

    private void events() {
        PluginManager pm = getServer().getPluginManager();

        // User
        pm.registerEvents(new UserJoinEvent(), this);
        pm.registerEvents(new UserPearlEvent(), this);
        pm.registerEvents(new UserQuitEvent(), this);
    }

    private void checkUsers() {
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                for (User user : users.values()) {
                    if (user.isModified()) {
                        user.save();
                        count++;
                    }
                }
                getLogger().info("Saved " + count + " user(s)");
            }
        }.runTaskTimer(this, 0, 20 * 60 * 5);
    }

    private void checkFactions() {
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                for (Faction faction : factions.values()) {
                    if (faction.isModified()) {
                        faction.save();
                        count++;
                    }
                }
                getLogger().info("Saved " + count + " faction(s)");
            }
        }.runTaskTimer(this, 0, 20 * 60 * 5);
    }

    public void addUser(Player p) {
        User existing = users.get(p.getUniqueId());
        if (existing != null) {
            userNames.remove(existing.getName().toUpperCase(Locale.ROOT));
        }
        User user = new User(p.getUniqueId(), p.getName());
        users.put(p.getUniqueId(), user);
        userNames.put(p.getName().toUpperCase(Locale.ROOT), user);
    }

    public User getUser(UUID uuid) {
        if (!users.containsKey(uuid)) {
            User user = new User(uuid);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && !player.getName().equals(user.getName())) {
                user.setName(player.getName());
                user.save();
            }
            users.put(uuid, user);
        }
        return users.get(uuid);
    }

    public User getUser(String name) {
        return userNames.get(name.toUpperCase(Locale.ROOT));
    }

    public void removeUser(UUID uuid) {
        User user = users.remove(uuid);
        if (user != null) {
            userNames.remove(user.getName().toUpperCase(Locale.ROOT));
            if (user.isModified()) user.save();
        }
    }

    public void addFaction(UUID uuid) {
        Faction faction = new Faction(uuid);
        factions.put(uuid, faction);
        factionNames.put(faction.getName().toUpperCase(Locale.ROOT), faction);
    }

    public void addFaction(Faction faction) {
        factions.put(faction.getUUID(), faction);
        factionNames.put(faction.getName().toUpperCase(Locale.ROOT), faction);
    }

    public Faction getFaction(UUID uuid) {
        if (!factions.containsKey(uuid)) {
            factions.put(uuid, new Faction(uuid));
        }
        return factions.get(uuid);
    }

    public Faction getFaction(String name) {
        return factionNames.get(name.toUpperCase(Locale.ROOT));
    }

    public void removeFaction(UUID uuid) {
        Faction faction = factions.remove(uuid);
        if (faction != null) {
            factionNames.remove(faction.getName().toUpperCase(Locale.ROOT));
            if (faction.isModified()) faction.save();
        }
    }

    public Map<UUID, User> getAllUsers() {
        return users;
    }
}
