package me.Thomas.HCF.IO.Faction;

import me.Thomas.HCF.IO.Claims.ChunkKey;
import me.Thomas.HCF.IO.Claims.Claim;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import me.Thomas.HCF.IO.Database.SaveSnapshot;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

public class Faction {


    public enum Type {
        SYSTEM,
        PLAYER,
        SAFEZONE,
        KOTH,
        ROAD
    }

    public enum MemberType {
        LEADER(3),
        COLEADER(2),
        CAPTAIN(1),
        MEMBER(0);

        private final int value;

        MemberType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static MemberType getChangedRank(MemberType actor, MemberType target, boolean promote) {
            if (actor != LEADER && actor != COLEADER) return null;
            if (target == null || target.value >= actor.value) return null;
            MemberType result = getMemberType(target.value + (promote ? 1 : -1));
            return result != null && result.value < actor.value ? result : null;
        }

        public static MemberType getMemberType(int value) {
            for (MemberType memberType : MemberType.values()) {
                if (memberType.getValue() == value) {
                    return memberType;
                }
            }
            return null;
        }
    }

    private final UUID uuid;

    private Type type;
    private String name;
    private UUID leader;
    private double balance;
    private int lives;
    private String color;
    private Location home;
    private Location rally;
    private boolean open;
    private double dtr;
    private long dtrFreezeExpiry;
    private long dtrNextRegen;
    private int points;
    private int kothCaptures;
    private String announcement;
    private boolean deathban;


    private long revision;
    private volatile long savedRevision;
    private Map<UUID, Integer> members = new HashMap<>();
    private boolean claiming;
    private volatile boolean disbanding;

    /**
     * Loads an existing faction by UUID.
     */
    public Faction(UUID uuid) {
        this.uuid = uuid;
        load();
    }

    /**
     * Creates a new system faction; the caller schedules persistence.
     */
    public Faction(UUID uuid, String name) {
        this.uuid = uuid;
        this.type = Type.SYSTEM;
        this.name = name;
        this.leader = null;
        this.balance = 0.0;
        this.lives = 0;
        this.color = "BLUE";
        this.home = null;
        this.members = new HashMap<>();
        this.revision++;
        this.open = false;
        this.dtr = 0;
        this.points = 0;
        this.kothCaptures = 0;
        this.announcement = null;
        this.deathban = true;
        this.claiming = false;
    }

    /**
     * Creates a new player faction; the caller schedules persistence.
     */
    public Faction(UUID uuid, UUID leader, String name) {
        this.uuid = uuid;
        this.type = Type.PLAYER;
        this.name = name;
        this.leader = leader;
        this.balance = 0.0;
        this.lives = 0;
        this.color = "BLUE";
        this.home = null;
        this.members = new HashMap<>();
        this.members.put(leader, MemberType.LEADER.getValue());
        this.revision++;
        this.open = false;
        this.dtr = 1.01;
        this.points = 0;
        this.kothCaptures = 0;
        this.announcement = null;
        this.deathban = true;
        this.claiming = false;
    }

    public static Faction get(UUID uuid) {
        return Main.getInstance().getFaction(uuid);
    }

    public void load() {
        String sql = "SELECT * FROM factions WHERE uuid = ?";

        try (
                Connection connection =
                        Main.getInstance()
                                .getDatabaseManager()
                                .getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, uuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Faction does not exist: " + uuid);
                }

                readRow(resultSet);
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not load faction data for " + uuid, exception);
        }
    }

    /** Hydrates an existing row without another query. */
    public Faction(ResultSet row) throws SQLException {
        this.uuid = UUID.fromString(row.getString("uuid"));
        readRow(row);
    }

    private void readRow(ResultSet resultSet) throws SQLException {
        this.type = Type.valueOf(
                resultSet.getString("type").toUpperCase(Locale.ROOT)
        );

        this.name = resultSet.getString("name");

        String leaderValue = resultSet.getString("leader");

        this.leader = leaderValue == null || leaderValue.isEmpty()
                ? null
                : UUID.fromString(leaderValue);

        this.balance = resultSet.getDouble("balance");
        this.lives = resultSet.getInt("lives");

        this.members = deserializeMembers(
                resultSet.getString("members")
        );

        this.color = resultSet.getString("color");
        this.home = deserializeLocation(
                resultSet.getString("home")
        );

        this.open = resultSet.getBoolean("open");

        this.dtr = resultSet.getDouble("dtr");
        this.dtrFreezeExpiry = resultSet.getLong("dtrFreezeExpiry");
        this.dtrNextRegen = resultSet.getLong("dtrNextRegen");

        this.points = resultSet.getInt("points");

        this.kothCaptures = resultSet.getInt("kothCaptures");

        this.announcement = resultSet.getString("announcement");

        this.deathban = resultSet.getBoolean("deathban");

        this.savedRevision = revision;

        this.claiming = false;
    }

    public void save() {
        SaveSnapshot snapshot = snapshot();
        if (snapshot != null) Main.getInstance().getDatabaseManager().save(List.of(snapshot)).join();
    }

    /** Call on the server thread before submitting database work. */
    public SaveSnapshot snapshot() {
        if (!isModified() || disbanding) return null;
        String sql =
                "INSERT INTO factions "
                        + "(uuid, type, name, leader, balance, lives, "
                        + "members, color, home, open, dtr, points, kothCaptures, announcement, deathban, dtrFreezeExpiry, dtrNextRegen) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(uuid) DO UPDATE SET "
                        + "type = excluded.type, "
                        + "name = excluded.name, "
                        + "leader = excluded.leader, "
                        + "balance = excluded.balance, "
                        + "lives = excluded.lives, "
                        + "members = excluded.members, "
                        + "color = excluded.color, "
                        + "home = excluded.home, "
                        + "open = excluded.open, "
                        + "dtr = excluded.dtr, "
                        + "points = excluded.points, "
                        + "kothCaptures = excluded.kothCaptures, "
                        + "announcement = excluded.announcement, "
                        + "deathban = excluded.deathban, "
                        + "dtrFreezeExpiry = excluded.dtrFreezeExpiry, "
                        + "dtrNextRegen = excluded.dtrNextRegen";
        long version = revision;
        return new SaveSnapshot(sql, () -> savedRevision = version,
                uuid.toString(), type.name(), name, leader == null ? null : leader.toString(),
                balance, lives, serializeMembers(), color, serializeLocation(home), open, dtr,
                points, kothCaptures, announcement, deathban, dtrFreezeExpiry, dtrNextRegen);
    }

    private String serializeMembers() {
        StringBuilder result = new StringBuilder();

        for (Map.Entry<UUID, Integer> entry : members.entrySet()) {
            if (!result.isEmpty()) {
                result.append(';');
            }

            result.append(entry.getKey())
                    .append('|')
                    .append(entry.getValue());
        }

        return result.toString();
    }

    private String serializeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        return location.getWorld().getName()
                + ";"
                + location.getX()
                + ";"
                + location.getY()
                + ";"
                + location.getZ()
                + ";"
                + location.getYaw()
                + ";"
                + location.getPitch();
    }

    private Location deserializeLocation(String serialized) {
        if (serialized == null || serialized.isEmpty()) {
            return null;
        }

        String[] values = serialized.split(";", -1);

        if (values.length != 6) {
            Main.getInstance().getLogger().log(
                    Level.WARNING,
                    "Ignoring malformed faction home: " + serialized
            );
            return null;
        }

        try {
            String worldName = values[0];
            World world = Bukkit.getWorld(worldName);

            if (world == null) {
                Main.getInstance().getLogger().log(
                        Level.WARNING,
                        "Could not find world for faction home: " + worldName
                );
                return null;
            }

            double x = Double.parseDouble(values[1]);
            double y = Double.parseDouble(values[2]);
            double z = Double.parseDouble(values[3]);
            float yaw = Float.parseFloat(values[4]);
            float pitch = Float.parseFloat(values[5]);

            return new Location(world, x, y, z, yaw, pitch);
        } catch (IllegalArgumentException exception) {
            Main.getInstance().getLogger().log(
                    Level.WARNING,
                    "Ignoring malformed faction home: " + serialized,
                    exception
            );
            return null;
        }
    }

    public static boolean exists(UUID uuid) {
        String sql = "SELECT 1 FROM factions WHERE uuid = ?";

        try (
                Connection connection =
                        Main.getInstance()
                                .getDatabaseManager()
                                .getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, uuid.toString());

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            Main.getInstance().getLogger().log(
                    Level.SEVERE,
                    "Could not check faction existence for " + uuid,
                    exception
            );

            return false;
        }
    }

    public UUID getUUID() {
        return uuid;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        if (Objects.equals(this.type, type)) return;
        this.type = type;
        this.revision++;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (Objects.equals(this.name, name)) return;
        this.name = name;
        this.revision++;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        if (Objects.equals(this.leader, leader)) return;
        this.leader = leader;
        this.revision++;
    }

    public boolean transferLeadership(UUID actor, UUID target) {
        if (type != Type.PLAYER || !Objects.equals(leader, actor) || !hasMember(actor)
                || !hasMember(target) || actor.equals(target)) return false;
        addMember(actor, MemberType.COLEADER.getValue());
        addMember(target, MemberType.LEADER.getValue());
        setLeader(target);
        return true;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        if (this.balance == balance) return;
        this.balance = balance;
        this.revision++;
    }

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {
        if (this.lives == lives) return;
        this.lives = lives;
        this.revision++;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        if (Objects.equals(this.color, color)) return;
        this.color = color;
        this.revision++;
    }

    public Location getHome() {
        return home == null ? null : home.clone();
    }

    public Location getRally() {
        return rally == null ? null : rally.clone();
    }

    public void setRally(Location rally) {
        this.rally = rally == null ? null : rally.clone();
    }

    public void setHome(Location home) {
        this.home = home == null ? null : home.clone();
        this.revision++;
    }

    public boolean hasHome() {
        return home != null;
    }

    public boolean isModified() {
        return revision != savedRevision;
    }

    public Map<UUID, Integer> getMembers() {
        return Collections.unmodifiableMap(members);
    }

    public void addMember(UUID member, int value) {
        Integer oldValue = members.put(member, value);

        if (oldValue == null || oldValue != value) {
            revision++;
        }
    }

    public void removeMember(UUID member) {
        if (members.remove(member) != null) {
            revision++;
        }
    }

    public boolean hasMember(UUID member) {
        return members.containsKey(member);
    }

    public boolean changeMemberRank(UUID actor, UUID target, boolean promote) {
        if (!hasMember(actor) || !hasMember(target) || actor.equals(target) || target.equals(leader)) return false;
        MemberType rank = MemberType.getChangedRank(getMemberType(actor), getMemberType(target), promote);
        if (rank == null) return false;
        addMember(target, rank.getValue());
        return true;
    }

    public Integer getMember(UUID member) {
        return members.get(member);
    }

    private Map<UUID, Integer> deserializeMembers(String serialized) {
        Map<UUID, Integer> result = new HashMap<>();

        if (serialized == null || serialized.isEmpty()) {
            return result;
        }

        for (String memberData : serialized.split(";")) {
            if (memberData.isEmpty()) {
                continue;
            }

            String[] values = memberData.split("\\|");

            if (values.length != 2) {
                continue;
            }

            try {
                UUID member = UUID.fromString(values[0]);
                int rank = Integer.parseInt(values[1]);

                result.put(member, rank);
            } catch (IllegalArgumentException exception) {
                Main.getInstance().getLogger().log(
                        Level.WARNING,
                        "Ignoring malformed member data: " + memberData,
                        exception
                );
            }
        }

        return result;
    }

    public Map<UUID, Integer> getOnlineMembers() {
        Map<UUID, Integer> result = new HashMap<>();

        for (UUID member : members.keySet()) {
            if (Bukkit.getPlayer(member) != null) {
                result.put(member, members.get(member));
            }
        }
        return result;
    }

    public void broadcastMemberConnection(Player player, boolean online) {
        if (type != Type.PLAYER || !hasMember(player.getUniqueId())) return;
        String key = online ? "faction.member.online" : "faction.member.offline";
        Component message = C.chat(Objects.requireNonNull(Main.getInstance().getLocaleConfig().getString(key))
                .replace("%player%", player.getName()));
        for (UUID uuid : members.keySet()) {
            if (uuid.equals(player.getUniqueId())) continue;
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) member.sendMessage(message);
        }
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        if (this.open == open) return;
        this.open = open;
        revision++;
    }

    public double getDTR() {
        return dtr;
    }

    public double getMaxDTR() {
        double memberMaximum = java.math.BigDecimal.valueOf(members.size())
                .multiply(new java.math.BigDecimal("1.01")).doubleValue();
        double configuredMaximum = Main.getInstance().getConfig().getDouble("factions.dtr.maximum", 6.06);
        if (!Double.isFinite(configuredMaximum) || configuredMaximum < 0) configuredMaximum = 6.06;
        return Math.min(memberMaximum, configuredMaximum);
    }

    public long getDTRFreezeExpiry() {
        return dtrFreezeExpiry;
    }

    public void setDTRFreezeExpiry(long expiry) {
        if (dtrFreezeExpiry == expiry) return;
        dtrFreezeExpiry = expiry;
        revision++;
    }

    public long getDTRNextRegen() {
        return dtrNextRegen;
    }

    public void setDTRNextRegen(long nextRegen) {
        if (dtrNextRegen == nextRegen) return;
        dtrNextRegen = nextRegen;
        revision++;
    }

    public void setDTR(double dtr) {
        if (this.dtr == dtr) return;
        this.dtr = dtr;
        revision++;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        if (this.points == points) return;
        this.points = points;
        revision++;
    }

    public int getKOTHCaptures() {
        return kothCaptures;
    }

    public void setKOTHCaptures(int kothCaptures) {
        if (this.kothCaptures == kothCaptures) return;
        this.kothCaptures = kothCaptures;
        revision++;
    }

    public String getAnnouncement() {
        return announcement;
    }

    public void setAnnouncement(String announcement) {
        if (Objects.equals(this.announcement, announcement)) return;
        this.announcement = announcement;
        revision++;
    }

    public static Faction get(String name) {
        return Main.getInstance().getFaction(name);
    }

    public MemberType getMemberType(UUID uuid) {
        return MemberType.getMemberType(members.getOrDefault(uuid, MemberType.MEMBER.getValue()));
    }

    public boolean isRaidable() {
        return dtr <= 0;
    }

    public boolean disband() {
        if (disbanding) return false;
        disbanding = true;
        try {
            Main.getInstance().getDatabaseManager().save(List.of(
                    new SaveSnapshot("UPDATE players SET faction = NULL WHERE faction = ?", () -> {}, uuid.toString()),
                    new SaveSnapshot("DELETE FROM factionInvites WHERE faction = ?", () -> {}, uuid.toString()),
                    new SaveSnapshot("DELETE FROM factions WHERE uuid = ?", () -> {}, uuid.toString())
            )).join();
            for (User user : Main.getInstance().getAllUsers().values()) {
                if (uuid.equals(user.getFaction())) {
                    user.setFaction(null);
                    user.saveAsync();
                }
            }
            Main.getInstance().removeClaimsForFaction(uuid);
            Main.getInstance().removeFaction(uuid);
            return true;
        } catch (java.util.concurrent.CompletionException exception) {
            disbanding = false;
            return false;
        }
    }

    public void saveAsync() {
        SaveSnapshot snapshot = snapshot();
        if (snapshot != null) Main.getInstance().getDatabaseManager().save(List.of(snapshot));
    }

    public static boolean isNameInvalid(@NotNull String name, CommandSender p) {
        if (Faction.get(name) != null) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.already-exists")));
            return true;
        }
        if (Main.getInstance().getConfig().getStringList("factions.name.blocked").stream()
                .anyMatch(name::equalsIgnoreCase)) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.blocked")));
            return true;
        }
        if (name.length() < Main.getInstance().getConfig().getInt("factions.name.min") || name.length() > Main.getInstance().getConfig().getInt("factions.name.max")) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.invalid-length")));
            return true;
        }
        return false;
    }

    public static ItemStack getClaimWand() {
        ItemStack wand = new ItemStack(Objects.requireNonNull(Material.matchMaterial(Objects.requireNonNull(Main.getInstance().getConfig().getString("factions.claim-wand.item")))));
        ItemMeta meta = wand.getItemMeta();
        ArrayList<Component> lore = new ArrayList<>();
        for (String s : Main.getInstance().getConfig().getStringList("factions.claim-wand.lore")) {
            lore.add(C.chat(s));
        }
        meta.lore(lore);
        meta.displayName(C.chat(Main.getInstance().getConfig().getString("factions.claim-wand.name")));
        wand.setItemMeta(meta);
        return wand;
    }

    public static Faction getAtLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        int x = location.getBlockX();
        int z = location.getBlockZ();

        ChunkKey key = new ChunkKey(
                location.getWorld().getUID(),
                Math.floorDiv(x, 16),
                Math.floorDiv(z, 16)
        );

        List<Claim> possibleClaims =
                Main.getInstance().getClaimsByChunk(key);

        if (possibleClaims == null) {
            return null;
        }

        for (Claim claim : possibleClaims) {
            if (claim.contains(x, z)) {
                return Faction.get(claim.faction());
            }
        }

        return null;
    }

    public boolean hasClaim() {
        return Main.getInstance().hasClaim(uuid);
    }

    public static boolean isInWilderness(Location location) {
        if (location == null || location.getWorld() == null) return false;
        int warzoneLimit;
        switch (location.getWorld().getEnvironment()) {
            case NETHER -> warzoneLimit = Main.getInstance().getConfig().getInt("factions.sizes.worlds.nether.warzone");
            case THE_END -> {
                return true;
            }
            default -> warzoneLimit = Main.getInstance().getConfig().getInt("factions.sizes.worlds.default.warzone");
        }

        int x = location.getBlockX();
        int z = location.getBlockZ();

        return Math.abs((long) x) > warzoneLimit
                || Math.abs((long) z) > warzoneLimit;
    }

    public static boolean isInWarzoneBuildLimit(Location location) {
        if (location == null || location.getWorld() == null) return false;
        int warzoneLimit;
        switch (location.getWorld().getEnvironment()) {
            case NETHER -> warzoneLimit = Main.getInstance().getConfig().getInt("factions.sizes.worlds.nether.warzone-build-limit");
            case THE_END -> {
                return true;
            }
            default -> warzoneLimit = Main.getInstance().getConfig().getInt("factions.sizes.worlds.default.warzone-build-limit");
        }

        int x = location.getBlockX();
        int z = location.getBlockZ();

        return !(Math.abs((long) x) > warzoneLimit
                || Math.abs((long) z) > warzoneLimit);
    }

    public boolean isDeathban() {
        return deathban;
    }

    public void setDeathban(boolean deathban) {
        if (this.deathban == deathban) return;
        this.deathban = deathban;
        revision++;
    }

    public String getFormattedName(User receiver) {
        String name;
        switch (type) {
            case PLAYER -> {
                if (!receiver.hasFaction()) {
                    name = C.getColorCode(Objects.requireNonNull(Main.getInstance().getConfig().getString("factions.colors.enemy"))) + this.name;
                } else if (receiver.getFaction().equals(this.uuid)) {
                    name = C.getColorCode(Objects.requireNonNull(Main.getInstance().getConfig().getString("factions.colors.team"))) + this.name;
                } else {
                    name = C.getColorCode(Objects.requireNonNull(Main.getInstance().getConfig().getString("factions.colors.enemy"))) + this.name;
                }
            }
            case KOTH -> name = C.getColorCode(this.color) + this.name + " KOTH";
            case ROAD -> name = C.getColorCode(this.color) + this.name + " Road";
            default -> name = C.getColorCode(this.color) + this.name;
        }
        return name;
    }

    public static String getNameAtLocation(Location location, User user) {
        Faction faction = getAtLocation(location);
        if (faction != null) {
            return faction.getFormattedName(user);
        }
        if (isInWilderness(location)) {
            return Main.getInstance().getLocaleConfig().getString("faction.wilderness");
        }
        return Main.getInstance().getLocaleConfig().getString("faction.warzone");
    }

    public boolean isClaiming() {
        return claiming;
    }

    public void setClaiming(boolean claiming) {
        this.claiming = claiming;
    }

    public static List<Claim> getClaimsNearChunk(ChunkKey chunkKey, int radius) {
        if (chunkKey == null) {
            return Collections.emptyList();
        }
        if (radius < 0) {
            throw new IllegalArgumentException("Radius cannot be negative");
        }

        Set<Claim> nearbyClaims = new LinkedHashSet<>();

        for (int chunkX = chunkKey.chunkX() - radius; chunkX <= chunkKey.chunkX() + radius; chunkX++) {
            for (int chunkZ = chunkKey.chunkZ() - radius; chunkZ <= chunkKey.chunkZ() + radius; chunkZ++) {
                List<Claim> claimsInChunk = Main.getInstance().getClaimsByChunk(
                        new ChunkKey(chunkKey.worldId(), chunkX, chunkZ)
                );

                if (claimsInChunk != null) {
                    nearbyClaims.addAll(claimsInChunk);
                }
            }
        }

        return new ArrayList<>(nearbyClaims);
    }
}
