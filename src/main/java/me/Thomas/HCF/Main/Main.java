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
import me.Thomas.HCF.IO.Claims.ClaimIndex;
import me.Thomas.HCF.IO.Claims.Claiming;
import me.Thomas.HCF.IO.Database.DatabaseManager;
import me.Thomas.HCF.IO.Database.SaveSnapshot;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Scoreboard.BoardHandler;
import me.Thomas.HCF.Utils.Timer.ServerTimer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

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
    private boolean dataLoaded;

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
    private final ClaimIndex claimIndex = new ClaimIndex();

    // Server Timers
    private final Map<ServerTimer.Type, ServerTimer> serverTimers = new HashMap<>();

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

        loadData();
        loadInvites();
        loadClaims();
        dataLoaded = true;
        boardHandler = new BoardHandler();

        getLogger().info("dHCF has been enabled with SQLite support!");
        events();
        commands();

        Bukkit.getScheduler().runTaskTimer(this, this::saveAll, 20 * 60 * 5, 20 * 60 * 5);
    }

    @Override
    public void onDisable() {
        if (boardHandler != null) boardHandler.close();
        if (databaseManager != null) {
            if (dataLoaded) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    User user = users.get(player.getUniqueId());
                    if (user != null) user.pauseTimers();
                }
                saveAll();
            }
            databaseManager.close();
        }

        getLogger().info("dHCF has been disabled.");
    }

    private void commands() {

        // User
        FactionCommand factionCommand = new FactionCommand();
        Objects.requireNonNull(getCommand("faction")).setExecutor(factionCommand);
        Objects.requireNonNull(getCommand("faction")).setTabCompleter(factionCommand);
        BalanceCommand balanceCommand = new BalanceCommand();
        Objects.requireNonNull(getCommand("balance")).setExecutor(balanceCommand);
        Objects.requireNonNull(getCommand("balance")).setTabCompleter(balanceCommand);

        // Admin
        EconomyCommand economyCommand = new EconomyCommand();
        Objects.requireNonNull(getCommand("economy")).setExecutor(economyCommand);
        Objects.requireNonNull(getCommand("economy")).setTabCompleter(economyCommand);
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
            SELECT i.faction, i.user
            FROM factionInvites i JOIN factions f ON f.uuid = i.faction
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
            throw new IllegalStateException("Could not load faction invites", exception);
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
        databaseManager.save(List.of(new SaveSnapshot(
                "INSERT OR IGNORE INTO factionInvites (faction, user) VALUES (?, ?)", () -> {},
                factionUuid.toString(), userUuid.toString())));
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
        databaseManager.save(List.of(new SaveSnapshot(
                "DELETE FROM factionInvites WHERE faction = ? AND user = ?", () -> {},
                factionUuid.toString(), userUuid.toString())));
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
                User user = new User(usersResultSet);
                users.put(user.getUUID(), user);
                userNames.put(user.getName().toUpperCase(Locale.ROOT), user);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load persisted data", exception);
        }

        // 2. Process Factions independently
        try (
                Connection conn2 = databaseManager.getConnection();
                PreparedStatement factionsStatement = conn2.prepareStatement(factionsSql);
                ResultSet factionsResultSet = factionsStatement.executeQuery()
        ) {
            while (factionsResultSet.next()) {
                Faction faction = new Faction(factionsResultSet);
                factions.put(faction.getUUID(), faction);
                factionNames.put(faction.getName().toUpperCase(Locale.ROOT), faction);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not load persisted data", exception);
        }
        for (User user : users.values()) {
            if (user.hasFaction() && !factions.containsKey(user.getFaction())) user.setFaction(null);
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
            claimIndex.clear();

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
            throw new IllegalStateException("Could not load faction claims", exception);
        }
    }

    private void indexClaim(Claim claim) {
        claimIndex.add(claim);
    }

    public boolean intersectsClaim(Claim claim) { return claimIndex.intersects(claim); }
    public boolean hasClaim(UUID faction) { return claimIndex.hasFaction(faction); }

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

    private void saveAll() {
        List<SaveSnapshot> snapshots = new ArrayList<>();
        for (Faction faction : factions.values()) {
            SaveSnapshot snapshot = faction.snapshot();
            if (snapshot != null) snapshots.add(snapshot);
        }
        for (User user : users.values()) {
            SaveSnapshot snapshot = user.snapshot();
            if (snapshot != null) snapshots.add(snapshot);
        }
        databaseManager.save(snapshots);
    }

    public void saveTogether(User user, Faction faction) {
        List<SaveSnapshot> snapshots = new ArrayList<>();
        SaveSnapshot factionSnapshot = faction.snapshot();
        SaveSnapshot userSnapshot = user.snapshot();
        if (factionSnapshot != null) snapshots.add(factionSnapshot);
        if (userSnapshot != null) snapshots.add(userSnapshot);
        databaseManager.save(snapshots);
    }

    public void addUser(Player p) {
        User user = users.get(p.getUniqueId());
        if (user == null) {
            user = new User(p.getUniqueId(), p.getName());
            users.put(p.getUniqueId(), user);
        } else {
            userNames.remove(user.getName().toUpperCase(Locale.ROOT), user);
            user.setName(p.getName());
        }
        userNames.put(p.getName().toUpperCase(Locale.ROOT), user);
        user.saveAsync();
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
            userNames.put(user.getName().toUpperCase(Locale.ROOT), user);
        }
        return users.get(uuid);
    }

    public User getUser(String name) {
        return userNames.get(name.toUpperCase(Locale.ROOT));
    }

    public void removeUser(UUID uuid) {
        // Offline profiles remain available to faction and economy commands.
        User user = users.get(uuid);
        if (user != null) {
            user.setClaiming(null);
            user.setBypass(false);
            user.setLastEnteredFaction(null);
            user.getMap().clear();
            user.removeTimer(me.Thomas.HCF.Utils.Timer.Timer.Type.HOME);
            user.saveAsync();
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
        return factions.get(uuid);
    }

    public Faction getFaction(String name) {
        return factionNames.get(name.toUpperCase(Locale.ROOT));
    }

    public void removeFaction(UUID uuid) {
        Set<UUID> invitedUsers = factionInvites.remove(uuid);
        if (invitedUsers != null) {
            for (UUID userUuid : invitedUsers) {
                Set<UUID> invites = userInvites.get(userUuid);
                if (invites != null) {
                    invites.remove(uuid);
                    if (invites.isEmpty()) userInvites.remove(userUuid);
                }
            }
        }
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

        claimIndex.removeFaction(factionId);
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
        return claimIndex.atChunk(key);
    }

    public boolean saveClaim(Claim claim) {
        return saveClaim(claim, 0);
    }

    private boolean saveClaim(Claim claim, double cost) {
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

        Faction faction = factions.get(claim.faction());
        if (faction == null || !Double.isFinite(cost) || cost < 0 || faction.getBalance() < cost) return false;
        double previousBalance = faction.getBalance();
        if (cost > 0) faction.setBalance(previousBalance - cost);
        List<SaveSnapshot> snapshots = new ArrayList<>();
        SaveSnapshot factionSnapshot = faction.snapshot();
        if (factionSnapshot != null) snapshots.add(factionSnapshot);
        snapshots.add(new SaveSnapshot(sql, () -> {}, claim.faction().toString(), claim.world().toString(),
                claim.minX(), claim.maxX(), claim.minZ(), claim.maxZ()));
        try {
            databaseManager.save(snapshots).join();
            return true;
        } catch (java.util.concurrent.CompletionException exception) {
            if (cost > 0) faction.setBalance(previousBalance);
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
        return addClaim(faction, one, two, 0);
    }

    public boolean addClaim(UUID faction, Location one, Location two, double cost) {
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

        if (candidate.intersects()) return false;

        if (!saveClaim(candidate, cost)) {
            return false;
        }

        claims.add(candidate);
        indexClaim(candidate);
        return true;
    }

    public boolean hasServerTimer(ServerTimer.Type type) {
        return serverTimers.containsKey(type);
    }

    public ServerTimer getServerTimer(ServerTimer.Type type) {
        return serverTimers.get(type);
    }

    public void addServerTimer(ServerTimer timer) {
        serverTimers.put(timer.getType(), timer);
    }

    public void removeServerTimer(ServerTimer.Type type) {
        serverTimers.remove(type);
    }

    public void pauseServerTimer(ServerTimer.Type type) {
        ServerTimer timer = serverTimers.get(type);
        if (timer != null) timer.pause();
    }

    public void sendMessage(CommandSender sender, String message) {
        for (String s : messagesConfig.getStringList(message)) {
            sender.sendMessage(C.chat(s));
        }
    }
}
