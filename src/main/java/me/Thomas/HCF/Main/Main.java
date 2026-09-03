package me.Thomas.HCF.Main;

import me.Thomas.HCF.Commands.User.Economy.BalanceCommand;
import me.Thomas.HCF.Commands.User.Faction.EconomyCommand;
import me.Thomas.HCF.Commands.User.Faction.FactionCommand;
import me.Thomas.HCF.Events.Player.Connection.UserJoinEvent;
import me.Thomas.HCF.Events.Player.Connection.UserQuitEvent;
import me.Thomas.HCF.Events.Player.Item.UserClaimEvent;
import me.Thomas.HCF.Events.Player.Item.UserPearlEvent;
import me.Thomas.HCF.Events.Player.Interact.UserInteractInFactionEvent;
import me.Thomas.HCF.Events.Player.Movement.UserMoveFactionEvent;
import me.Thomas.HCF.IO.Claims.ChunkKey;
import me.Thomas.HCF.IO.Claims.Claim;
import me.Thomas.HCF.IO.Claims.Claiming;
import me.Thomas.HCF.IO.Database.DatabaseManager;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Utils.Scoreboard.BoardHandler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

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
    private boolean factionDebug = false;

    private final Map<UUID, Set<UUID>> userInvites = new HashMap<>();
    private final Map<UUID, Set<UUID>> factionInvites = new HashMap<>();

    // Claims
    private final List<Claim> claims = new ArrayList<>();
    private final Map<ChunkKey, List<Claim>> claimsByChunk = new HashMap<>();

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
        loadInvites();
        loadClaims();

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

        getLogger().info("dHCF has been disabled.");
    }

    private void commands() {

        // User
        Objects.requireNonNull(getCommand("faction")).setExecutor(new FactionCommand()); Objects.requireNonNull(getCommand("faction")).setTabCompleter(new FactionCommand());
        Objects.requireNonNull(getCommand("balance")).setExecutor(new BalanceCommand()); Objects.requireNonNull(getCommand("balance")).setTabCompleter(new BalanceCommand());

        // Admin
        Objects.requireNonNull(getCommand("economy")).setExecutor(new EconomyCommand()); Objects.requireNonNull(getCommand("economy")).setTabCompleter(new EconomyCommand());
    }

    private void events() {
        PluginManager pm = getServer().getPluginManager();

        // User
        pm.registerEvents(new UserJoinEvent(), this);
        pm.registerEvents(new UserPearlEvent(), this);
        pm.registerEvents(new UserQuitEvent(), this);
        pm.registerEvents(new UserClaimEvent(), this);
        pm.registerEvents(new UserMoveFactionEvent(), this);
        pm.registerEvents(new UserInteractInFactionEvent(), this);
    }

    private void loadInvites() {
        String sql = """
            SELECT faction, user
            FROM factionInvites
            """;

        try (
                Connection connection = databaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                UUID factionUuid = UUID.fromString(
                        resultSet.getString("faction")
                );

                UUID userUuid = UUID.fromString(
                        resultSet.getString("user")
                );

                userInvites
                        .computeIfAbsent(userUuid, key -> new HashSet<>())
                        .add(factionUuid);

                factionInvites
                        .computeIfAbsent(factionUuid, key -> new HashSet<>())
                        .add(userUuid);
            }
        } catch (SQLException | IllegalArgumentException exception) {
            getLogger().log(
                    Level.SEVERE,
                    "Could not load faction invites",
                    exception
            );
        }
    }

    public Set<UUID> getInvitesForUser(UUID userUuid) {
        return new HashSet<>(
                userInvites.getOrDefault(userUuid, Collections.emptySet())
        );
    }

    public Set<UUID> getInvitesForFaction(UUID factionUuid) {
        return new HashSet<>(
                factionInvites.getOrDefault(factionUuid, Collections.emptySet())
        );
    }

    public boolean addInvite(UUID factionUuid, UUID userUuid) {
        boolean added = userInvites
                .computeIfAbsent(userUuid, key -> new HashSet<>())
                .add(factionUuid);

        factionInvites
                .computeIfAbsent(factionUuid, key -> new HashSet<>())
                .add(userUuid);

        if (added) {
            saveInviteAsync(factionUuid, userUuid);
        }

        return added;
    }

    private void saveInviteAsync(UUID factionUuid, UUID userUuid) {
        String sql = """
            INSERT OR IGNORE INTO factionInvites (faction, user)
            VALUES (?, ?)
            """;

        Bukkit.getScheduler().runTaskAsynchronously(
                this,
                () -> {
                    try (
                            Connection connection =
                                    databaseManager.getConnection();
                            PreparedStatement statement =
                                    connection.prepareStatement(sql)
                    ) {
                        statement.setString(1, factionUuid.toString());
                        statement.setString(2, userUuid.toString());
                        statement.executeUpdate();
                    } catch (SQLException exception) {
                        getLogger().log(
                                Level.SEVERE,
                                "Could not save faction invite",
                                exception
                        );
                    }
                }
        );
    }

    public boolean removeInvite(UUID factionUuid, UUID userUuid) {
        boolean removed = false;

        Set<UUID> userFactionInvites = userInvites.get(userUuid);

        if (userFactionInvites != null) {
            removed = userFactionInvites.remove(factionUuid);

            if (userFactionInvites.isEmpty()) {
                userInvites.remove(userUuid);
            }
        }

        Set<UUID> factionUserInvites = factionInvites.get(factionUuid);

        if (factionUserInvites != null) {
            factionUserInvites.remove(userUuid);

            if (factionUserInvites.isEmpty()) {
                factionInvites.remove(factionUuid);
            }
        }

        if (removed) {
            deleteInviteAsync(factionUuid, userUuid);
        }

        return removed;
    }

    private void deleteInviteAsync(UUID factionUuid, UUID userUuid) {
        String sql = """
            DELETE FROM factionInvites
            WHERE faction = ? AND user = ?
            """;

        Bukkit.getScheduler().runTaskAsynchronously(
                this,
                () -> {
                    try (
                            Connection connection =
                                    databaseManager.getConnection();
                            PreparedStatement statement =
                                    connection.prepareStatement(sql)
                    ) {
                        statement.setString(1, factionUuid.toString());
                        statement.setString(2, userUuid.toString());
                        statement.executeUpdate();
                    } catch (SQLException exception) {
                        getLogger().log(
                                Level.SEVERE,
                                "Could not delete faction invite",
                                exception
                        );
                    }
                }
        );
    }

    public boolean hasInvite(UUID factionUuid, UUID userUuid) {
        return userInvites
                .getOrDefault(userUuid, Collections.emptySet())
                .contains(factionUuid);
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

        // 1. Process Users independently
        try (
                Connection conn1 = databaseManager.getConnection();
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
                Connection conn2 = databaseManager.getConnection();
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

    private void loadClaims() {
        String sql = """
            SELECT claimID, faction, world, minX, maxX, minZ, maxZ
            FROM factionClaims
        """;

        try (
                Connection connection = databaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet results = statement.executeQuery()
        ) {
            claims.clear();

            while (results.next()) {
                Claim claim = new Claim(
                        UUID.fromString(results.getString("faction")),
                        UUID.fromString(results.getString("world")),
                        results.getInt("minX"),
                        results.getInt("maxX"),
                        results.getInt("minZ"),
                        results.getInt("maxZ")
                );

                claims.add(claim);
                indexClaim(claim);
            }

            getLogger().info("Loaded " + claims.size() + " faction claim(s).");
        } catch (SQLException | IllegalArgumentException exception) {
            getLogger().log(
                    Level.SEVERE,
                    "Could not load faction claims",
                    exception
            );
        }
    }

    private void indexClaim(Claim claim) {
        int minChunkX = Math.floorDiv(claim.minX(), 16);
        int maxChunkX = Math.floorDiv(claim.maxX(), 16);
        int minChunkZ = Math.floorDiv(claim.minZ(), 16);
        int maxChunkZ = Math.floorDiv(claim.maxZ(), 16);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkKey key = new ChunkKey(
                        claim.world(),
                        chunkX,
                        chunkZ
                );

                claimsByChunk
                        .computeIfAbsent(key, ignored -> new ArrayList<>())
                        .add(claim);
            }
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

    private void checkUsers() {
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                for (User user : users.values()) {
                    if (user.isModified()) {
                        user.saveAsync();
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
                        faction.saveAsync();
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
                user.saveAsync();
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
            if (user.isModified()) user.saveAsync();
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
        }
    }

    public void removeClaimsForFaction(UUID factionId) {
        for (User user : users.values()) {
            List<Claim> displayedClaims = user.getMap().stream()
                    .filter(claim -> claim.faction().equals(factionId))
                    .toList();

            Player player = Bukkit.getPlayer(user.getUUID());
            if (player != null) {
                for (Claim claim : displayedClaims) {
                    Claiming.resetClaimPillars(claim, player);
                }
            }

            user.getMap().removeAll(displayedClaims);
        }

        claims.removeIf(claim -> claim.faction().equals(factionId));

        claimsByChunk.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(claim -> claim.faction().equals(factionId));
            return entry.getValue().isEmpty();
        });
    }

    public void renameFaction(UUID uuid, String name) {
        Faction faction = factions.get(uuid);
        if (faction != null) {
            factionNames.remove(faction.getName().toUpperCase(Locale.ROOT));
            factionNames.put(name.toUpperCase(Locale.ROOT), faction);
        }
    }

    public Map<UUID, User> getAllUsers() {
        return users;
    }

    public Map<UUID, Faction> getAllFactions() {
        return factions;
    }

    public boolean isFactionDebug() {
        return factionDebug;
    }

    public void setFactionDebug(boolean factionDebug) {
        this.factionDebug = factionDebug;
    }


    public List<Claim> getClaimsByChunk(ChunkKey key) {
        return claimsByChunk.get(key);
    }

    public boolean saveClaim(Claim claim) {
        String sql = """
        INSERT INTO factionClaims (
            faction,
            world,
            minX,
            maxX,
            minZ,
            maxZ
        )
        VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (
                Connection connection = databaseManager.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, claim.faction().toString());
            statement.setString(2, claim.world().toString());
            statement.setInt(3, claim.minX());
            statement.setInt(4, claim.maxX());
            statement.setInt(5, claim.minZ());
            statement.setInt(6, claim.maxZ());

            statement.executeUpdate();
            return true;
        } catch (SQLException exception) {
            getLogger().log(
                    Level.SEVERE,
                    "Could not save faction claim",
                    exception
            );

            return false;
        }
    }

    public List<Claim> getClaims() {
        return claims;
    }

    public boolean addClaim(
            UUID faction,
            Location one,
            Location two
    ) {
        if (one.getWorld() == null || two.getWorld() == null) {
            return false;
        }

        if (!one.getWorld().equals(two.getWorld())) {
            return false;
        }

        int firstX = one.getBlockX();
        int secondX = two.getBlockX();
        int firstZ = one.getBlockZ();
        int secondZ = two.getBlockZ();

        int minX = Math.min(firstX, secondX);
        int maxX = Math.max(firstX, secondX);
        int minZ = Math.min(firstZ, secondZ);
        int maxZ = Math.max(firstZ, secondZ);

        UUID worldId = one.getWorld().getUID();

        Claim candidate = new Claim(
                faction,
                worldId,
                minX,
                maxX,
                minZ,
                maxZ
        );

        for (Claim existing : claims) {
            if (existing.intersects(candidate)) {
                return false;
            }
        }

        if (!saveClaim(candidate)) {
            return false;
        }

        claims.add(candidate);
        indexClaim(candidate);
        return true;
    }
}
