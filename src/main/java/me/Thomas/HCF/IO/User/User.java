package me.Thomas.HCF.IO.User;

import me.Thomas.HCF.IO.Claims.Claim;
import me.Thomas.HCF.IO.Claims.Claiming;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Timer.Timer;
import me.Thomas.HCF.Utils.Timer.ServerTimer;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;

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
    private int killstreak;
    private int highestKillstreak;
    private int deaths;
    private int lives;
    private long deathbanExpiry;
    private boolean pvpProtectionPending;
    private boolean reclaimed;
    private boolean mountainMessages = true;
    private boolean messages = true;
    private boolean messageSounds = true;
    private boolean scoreboard = true;
    private boolean publicChat = true;
    private boolean requestReportCooldowns = true;
    private boolean abilityCooldowns = true;
    private byte[] lastInventory;
    private Integer lastExperience;
    private final Set<Material> filteredItems = EnumSet.noneOf(Material.class);

    private final Set<UUID> ignoredPlayers = new HashSet<>();
    public record Note(int id, String author, String text, long createdAt) {}
    private final Map<Integer, Note> notes = new LinkedHashMap<>();
    private int nextNoteId = 1;

    private long revision;
    private volatile long savedRevision;
    private Claiming claiming;
    private UUID lastEnteredFaction;
    private UUID lastConversation;
    private boolean socialSpy;
    private boolean bypass;
    private boolean bardActive;
    private double bardEnergy;
    private ArrayList<Claim> map;

    private final Map<String, ServerTimer> customTimers = new LinkedHashMap<>();
    private final Map<String, ServerTimer> abilityTimers = new LinkedHashMap<>();

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
        this.killstreak = resultSet.getInt("killstreak");
        this.highestKillstreak = resultSet.getInt("highestKillstreak");
        this.deaths = resultSet.getInt("deaths");
        this.lives = resultSet.getInt("lives");

        this.timers = deserialize(
                resultSet.getString("timers")
        );

        this.deathbanExpiry = resultSet.getLong("deathbanExpiry");
        this.pvpProtectionPending = resultSet.getBoolean("pvpProtectionPending");
        this.reclaimed = resultSet.getBoolean("reclaimed");
        this.mountainMessages = resultSet.getBoolean("mountainMessages");
        this.messages = resultSet.getBoolean("messages");
        this.messageSounds = resultSet.getBoolean("messageSounds");
        this.scoreboard = resultSet.getBoolean("scoreboard");
        this.publicChat = resultSet.getBoolean("publicChat");
        this.requestReportCooldowns = resultSet.getBoolean("requestReportCooldowns");
        this.abilityCooldowns = resultSet.getBoolean("abilityCooldowns");
        filteredItems.clear();
        String filter = resultSet.getString("filteredItems");
        if (filter != null && !filter.isEmpty()) for (String item : filter.split(",")) {
            Material material = Material.matchMaterial(item);
            if (material != null && !material.isLegacy() && material.isItem() && !material.isAir()) filteredItems.add(material);
        }
        ignoredPlayers.clear();
        String ignored = resultSet.getString("ignoredPlayers");
        if (ignored != null && !ignored.isEmpty()) for (String player : ignored.split(",")) {
            try { ignoredPlayers.add(UUID.fromString(player)); }
            catch (IllegalArgumentException exception) { Main.getInstance().getLogger().warning("Invalid ignored player for " + uuid); }
        }
        readNotes(resultSet.getString("notes"));
        this.lastInventory = resultSet.getBytes("lastInventory");
        int experience = resultSet.getInt("lastExperience");
        this.lastExperience = resultSet.wasNull() ? null : experience;


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
        this.killstreak = 0;
        this.highestKillstreak = 0;
        this.deaths = 0;
        this.lives = 0;
        this.timers = new EnumMap<>(Timer.Type.class);
        this.deathbanExpiry = 0;
        this.pvpProtectionPending = false;
        this.reclaimed = false;
        this.mountainMessages = true;
        this.messages = true;
        this.messageSounds = true;
        this.scoreboard = true;
        this.publicChat = true;
        this.requestReportCooldowns = true;
        this.abilityCooldowns = true;
        filteredItems.clear();
        ignoredPlayers.clear();
        notes.clear();
        nextNoteId = 1;
        this.lastInventory = null;
        this.lastExperience = null;
        this.revision++;
        this.claiming = null;
        this.lastEnteredFaction = null;
        this.bypass = false;
        this.map = new ArrayList<>();

    }

    public boolean isBardActive() { return bardActive; }

    public void setBardActive(boolean active) { bardActive = active; }

    public double getBardEnergy() { return bardEnergy; }

    public void setBardEnergy(double energy) { bardEnergy = Double.isFinite(energy) ? Math.max(0, energy) : 0; }

    public boolean isSocialSpy() { return socialSpy; }

    public void setSocialSpy(boolean enabled) { socialSpy = enabled; }

    public UUID getLastConversation() { return lastConversation; }

    public void setLastConversation(UUID uuid) { lastConversation = uuid; }

    public boolean isMountainMessages() { return mountainMessages; }

    public void setMountainMessages(boolean enabled) {
        if (mountainMessages == enabled) return;
        mountainMessages = enabled;
        revision++;
    }

    public boolean isMessages() { return messages; }

    public void setMessages(boolean enabled) {
        if (messages == enabled) return;
        messages = enabled;
        revision++;
    }

    public boolean isMessageSounds() { return messageSounds; }

    public void setMessageSounds(boolean enabled) {
        if (messageSounds == enabled) return;
        messageSounds = enabled;
        revision++;
    }

    public boolean isScoreboard() { return scoreboard; }

    public void setScoreboard(boolean enabled) {
        if (scoreboard == enabled) return;
        scoreboard = enabled;
        revision++;
    }

    public boolean isPublicChat() { return publicChat; }

    public void setPublicChat(boolean enabled) {
        if (publicChat == enabled) return;
        publicChat = enabled;
        revision++;
    }

    public Set<Material> getFilteredItems() { return Collections.unmodifiableSet(filteredItems); }

    public boolean isItemFiltered(Material material) { return filteredItems.contains(material); }

    public boolean toggleFilteredItem(Material material) {
        boolean added = filteredItems.add(material);
        if (!added) filteredItems.remove(material);
        revision++;
        return added;
    }

    public void clearFilteredItems() {
        if (filteredItems.isEmpty()) return;
        filteredItems.clear();
        revision++;
    }

    public Map<String, ServerTimer> getAbilityTimers() { return abilityTimers; }

    public boolean isAbilityCooldowns() { return abilityCooldowns; }

    public void setAbilityCooldowns(boolean enabled) {
        if (abilityCooldowns == enabled) return;
        abilityCooldowns = enabled;
        revision++;
    }

    public boolean isRequestReportCooldowns() { return requestReportCooldowns; }

    public void setRequestReportCooldowns(boolean enabled) {
        if (requestReportCooldowns == enabled) return;
        requestReportCooldowns = enabled;
        revision++;
    }

    public boolean hasReclaimed() { return reclaimed; }

    public void setReclaimed(boolean reclaimed) {
        if (this.reclaimed == reclaimed) return;
        this.reclaimed = reclaimed;
        revision++;
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
                        + "timers, deathbanExpiry, pvpProtectionPending, lastInventory, lastExperience, mountainMessages, messages, messageSounds, scoreboard, publicChat, requestReportCooldowns, reclaimed, abilityCooldowns, filteredItems, ignoredPlayers, notes, killstreak, highestKillstreak) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, faction=excluded.faction, "
                        + "balance=excluded.balance, kills=excluded.kills, deaths=excluded.deaths, "
                        + "lives=excluded.lives, timers=excluded.timers, deathbanExpiry=excluded.deathbanExpiry, "
                        + "pvpProtectionPending=excluded.pvpProtectionPending, "
                        + "lastInventory=excluded.lastInventory, lastExperience=excluded.lastExperience, mountainMessages=excluded.mountainMessages, "
                        + "messages=excluded.messages, messageSounds=excluded.messageSounds, "
                        + "scoreboard=excluded.scoreboard, publicChat=excluded.publicChat, "
                        + "requestReportCooldowns=excluded.requestReportCooldowns, reclaimed=excluded.reclaimed, abilityCooldowns=excluded.abilityCooldowns, filteredItems=excluded.filteredItems, ignoredPlayers=excluded.ignoredPlayers, notes=excluded.notes, killstreak=excluded.killstreak, highestKillstreak=excluded.highestKillstreak";
        long version = revision;
        return new SaveSnapshot(sql, () -> savedRevision = version,
                uuid.toString(), name, faction == null ? null : faction.toString(),
                balance, kills, deaths, lives, serializeTimers(), deathbanExpiry, pvpProtectionPending ? 1 : 0,
                getLastInventory(), lastExperience, mountainMessages ? 1 : 0,
                messages ? 1 : 0, messageSounds ? 1 : 0, scoreboard ? 1 : 0, publicChat ? 1 : 0, requestReportCooldowns ? 1 : 0, reclaimed ? 1 : 0, abilityCooldowns ? 1 : 0,
                filteredItems.stream().map(Material::name).sorted().collect(java.util.stream.Collectors.joining(",")),
                ignoredPlayers.stream().map(UUID::toString).sorted().collect(java.util.stream.Collectors.joining(",")), serializeNotes(), killstreak, highestKillstreak);
    }

    public boolean isIgnoring(UUID player) { return ignoredPlayers.contains(player); }

    public boolean toggleIgnoredPlayer(UUID player) {
        boolean ignored = ignoredPlayers.add(player);
        if (!ignored) ignoredPlayers.remove(player);
        revision++;
        return ignored;
    }

    public Collection<Note> getNotes() { return List.copyOf(notes.values()); }

    public Note addNote(String author, String text) {
        Note note = new Note(nextNoteId++, author, text, System.currentTimeMillis());
        notes.put(note.id(), note);
        revision++;
        return note;
    }

    public boolean removeNote(int id) {
        if (notes.remove(id) == null) return false;
        revision++;
        return true;
    }

    private String serializeNotes() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("next-id", nextNoteId);
        data.set("notes", notes.values().stream().map(note -> Map.of("id", note.id(), "author", note.author(),
                "text", note.text(), "created-at", note.createdAt())).toList());
        return data.saveToString();
    }

    private void readNotes(String stored) throws SQLException {
        notes.clear();
        nextNoteId = 1;
        if (stored == null || stored.isBlank()) return;
        YamlConfiguration data = new YamlConfiguration();
        try { data.loadFromString(stored); }
        catch (InvalidConfigurationException exception) { throw new SQLException("Invalid notes for " + uuid, exception); }
        nextNoteId = Math.max(1, data.getInt("next-id", 1));
        for (Map<?, ?> entry : data.getMapList("notes")) {
            if (!(entry.get("id") instanceof Number id) || !(entry.get("created-at") instanceof Number time)
                    || !(entry.get("author") instanceof String author) || !(entry.get("text") instanceof String text)) continue;
            if (id.intValue() < 1) continue;
            Note note = new Note(id.intValue(), author, text, time.longValue());
            notes.put(note.id(), note);
            nextNoteId = Math.max(nextNoteId, note.id() + 1);
        }
    }

    public byte[] getLastInventory() {
        return lastInventory == null ? null : lastInventory.clone();
    }

    public void setLastInventory(byte[] inventory) {
        if (Arrays.equals(lastInventory, inventory)) return;
        lastInventory = inventory == null ? null : inventory.clone();
        revision++;
    }

    public Integer getLastExperience() {
        return lastExperience;
    }

    public void setLastExperience(Integer experience) {
        if (Objects.equals(lastExperience, experience)) return;
        lastExperience = experience;
        revision++;
    }

    public long getDeathbanExpiry() {
        return deathbanExpiry;
    }

    public boolean reviveWithLife() {
        if (lives <= 0 || deathbanExpiry <= System.currentTimeMillis()) return false;
        int previousLives = lives;
        long previousExpiry = deathbanExpiry;
        boolean previousPending = pvpProtectionPending;
        setLives(previousLives - 1);
        setDeathbanExpiry(0L);
        setPvpProtectionPending(true);
        try {
            // Commit the life deduction and revival together before allowing login.
            save();
        } catch (RuntimeException exception) {
            setLives(previousLives);
            setDeathbanExpiry(previousExpiry);
            setPvpProtectionPending(previousPending);
            throw exception;
        }
        return true;
    }

    public void setPvpProtectionPending(boolean pending) {
        if (pvpProtectionPending == pending) return;
        pvpProtectionPending = pending;
        revision++;
    }

    /** Consume eligibility once, so ordinary reconnects cannot refill protection. */
    public void applyPendingPvpTimer() {
        if (!pvpProtectionPending) return;
        setPvpProtectionPending(false);
        if (!Main.getInstance().isSOTWOngoing()) givePvpTimer();
    }

    public boolean givePvpTimer() {
        return giveProtectionTimer(Timer.Type.PVP);
    }

    public boolean giveStartingTimer() {
        return giveProtectionTimer(Timer.Type.STARTING);
    }

    public boolean hasPvpProtection() {
        return hasTimer(Timer.Type.PVP) || hasTimer(Timer.Type.STARTING);
    }

    private boolean giveProtectionTimer(Timer.Type type) {
        if (type.getLength() <= 0) return false;
        Timer timer = new Timer(type, true);
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) timer.pause();
        addTimer(type, timer);
        if (player != null) {
            player.sendMessage(C.chat(Main.getInstance().getLocaleConfig()
                            .getTimerMessage("start", type)
                    .replace("%time%", C.formatChatTime(type.getLength()))));
        }
        return true;
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
            if (entry.getKey() == Timer.Type.HOME || entry.getKey() == Timer.Type.STUCK
                    || entry.getKey() == Timer.Type.LOGOUT) {
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
                if (type == Timer.Type.HOME || type == Timer.Type.STUCK || type == Timer.Type.LOGOUT) {
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
        me.Thomas.HCF.Events.Player.Connection.UserChatEvent.setFactionChat(uuid, false);
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

    public int getKillstreak() { return killstreak; }

    public int getHighestKillstreak() { return highestKillstreak; }

    public void setKillstreak(int killstreak) {
        killstreak = Math.max(0, killstreak);
        if (this.killstreak == killstreak) return;
        this.killstreak = killstreak;
        highestKillstreak = Math.max(highestKillstreak, killstreak);
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
        if (type == Timer.Type.COMBAT && timers.containsKey(Timer.Type.LOGOUT)) {
            removeTimer(Timer.Type.LOGOUT);
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.sendMessage(C.chat(Main.getInstance().getLocaleConfig()
                    .getString("commands.logout.cancel-combat")));
        }
        timers.put(type, timer);
        revision++;
        if (type == Timer.Type.PVP || type == Timer.Type.STARTING) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) updateProtectionTimers(player.getLocation());
        }
    }

    public Map<String, ServerTimer> getCustomTimers() {
        customTimers.values().removeIf(timer -> timer.getRemainingTimeMillis() <= 0);
        return customTimers;
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

    public void clearCooldowns() {
        // PvP protection is not a cooldown; retain it while clearing combat, item timers, and teleport warmups.
        if (timers.keySet().removeIf(type -> type != Timer.Type.PVP && type != Timer.Type.STARTING)) revision++;
    }

    public void expireTimers() {
        Iterator<Map.Entry<Timer.Type, Timer>> iterator = timers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Timer.Type, Timer> entry = iterator.next();
            if (entry.getKey() == Timer.Type.HOME || entry.getKey() == Timer.Type.STUCK
                    || entry.getKey() == Timer.Type.LOGOUT
                    || entry.getValue().getRemainingTimeMillis() > 0) continue;
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && entry.getKey() != Timer.Type.CHAT) {
                player.sendMessage(me.Thomas.HCF.Utils.Chat.C.chat(
                        Main.getInstance().getLocaleConfig().getTimerMessage("end", entry.getKey())));
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

    public void updateProtectionTimers(Location location) {
        Faction faction = Faction.getAtLocation(location);
        boolean paused = faction != null && faction.getType() == Faction.Type.SAFEZONE;
        for (Timer.Type type : List.of(Timer.Type.PVP, Timer.Type.STARTING)) {
            Timer timer = timers.get(type);
            if (timer == null || timer.getRemainingTimeMillis() <= 0 || timer.isPaused() == paused) continue;
            timer.pause();
            revision++;
        }
    }

    public void unpauseTimers() {
        Player player = Bukkit.getPlayer(uuid);
        Faction faction = player == null ? null : Faction.getAtLocation(player.getLocation());
        boolean safezone = faction != null && faction.getType() == Faction.Type.SAFEZONE;
        for (Timer timer : timers.values()) {
            boolean protection = timer.getType() == Timer.Type.PVP || timer.getType() == Timer.Type.STARTING;
            if (timer.isPauseOnLogout() && timer.isPaused() && !(protection && safezone)) {
                timer.pause();
                revision++;
            }
        }
        if (player != null) updateProtectionTimers(player.getLocation());
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
            Faction oldFaction = Faction.get(this.claiming.getFaction());
            if (oldFaction != null) oldFaction.setClaiming(false);
        }
        this.claiming = claiming;
        if (claiming != null && !claiming.isClaimingFor()) {
            Faction newFaction = Faction.get(claiming.getFaction());
            if (newFaction != null) newFaction.setClaiming(true);
        }
    }

    public UUID getLastEnteredFaction() {
        return lastEnteredFaction;
    }

    public void setLastEnteredFaction(UUID lastEnteredFaction) {
        this.lastEnteredFaction = lastEnteredFaction;
    }

    public boolean isInStaffMode() {
        Player p = Bukkit.getPlayer(uuid);
        return p != null && Main.getInstance().getStaffMode() != null && Main.getInstance().getStaffMode().isEnabled(p);
    }

    public boolean isVanished() {
        Player p = Bukkit.getPlayer(uuid);
        return p != null && Main.getInstance().getStaffMode() != null && Main.getInstance().getStaffMode().isVanished(p);
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
