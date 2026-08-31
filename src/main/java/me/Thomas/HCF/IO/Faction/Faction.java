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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import java.util.logging.Level;

public class Faction {

    private static final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

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
    private boolean open;
    private double dtr;
    private int points;
    private int kothCaptures;
    private String announcement;
    private boolean deathban;


    private boolean modified;
    private Map<UUID, Integer> members = new HashMap<>();
    private boolean claiming;

    /**
     * Loads an existing faction by UUID.
     */
    public Faction(UUID uuid) {
        this.uuid = uuid;
        load();
    }

    /**
     * Creates a system faction and saves it immediately.
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
        this.modified = true;
        this.open = false;
        this.dtr = 0;
        this.points = 0;
        this.kothCaptures = 0;
        this.announcement = null;
        this.deathban = true;
        this.claiming = false;
    }

    /**
     * Creates a player faction and saves it immediately.
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
        this.modified = true;
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
                    initializeDefaults();
                    saveAsync();
                    return;
                }

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

                this.points = resultSet.getInt("points");

                this.kothCaptures = resultSet.getInt("kothCaptures");

                this.announcement = resultSet.getString("announcement") == null ? null : resultSet.getString("announcement");

                this.deathban = resultSet.getBoolean("deathban");

                this.modified = false;

                this.claiming = false;
            }
        } catch (SQLException | IllegalArgumentException exception) {
            Main.getInstance().getLogger().log(
                    Level.SEVERE,
                    "Could not load faction data for " + uuid,
                    exception
            );
        }
    }

    private void initializeDefaults() {
        this.type = Type.SYSTEM;
        this.name = "";
        this.leader = null;
        this.balance = 0.0;
        this.lives = 0;
        this.color = "BLUE";
        this.home = null;
        this.members = new HashMap<>();
        this.modified = true;
        this.deathban = true;
        this.claiming = false;
    }

    public void save() {
        if (!modified) {
            return;
        }

        String sql =
                "INSERT INTO factions "
                        + "(uuid, type, name, leader, balance, lives, "
                        + "members, color, home, open, dtr, points, kothCaptures, announcement, deathban) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
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
                        + "deathban = excluded.deathban";

        String membersValue = serializeMembers();
        String homeValue = serializeLocation(home);

        try (
                Connection connection =
                        Main.getInstance()
                                .getDatabaseManager()
                                .getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, uuid.toString());
            statement.setString(2, type.name());
            statement.setString(3, name);

            if (leader == null) {
                statement.setNull(4, Types.VARCHAR);
            } else {
                statement.setString(4, leader.toString());
            }

            statement.setDouble(5, balance);
            statement.setInt(6, lives);
            statement.setString(7, membersValue);
            statement.setString(8, color);

            if (homeValue == null) {
                statement.setNull(9, Types.VARCHAR);
            } else {
                statement.setString(9, homeValue);
            }

            statement.setBoolean(10, open);
            statement.setDouble(11, dtr);
            statement.setInt(12, points);
            statement.setInt(13, kothCaptures);
            statement.setString(14, announcement == null ? null : announcement);
            statement.setBoolean(15, deathban);

            statement.executeUpdate();

            // Only mark it clean after the database operation succeeds.
            modified = false;
        } catch (SQLException exception) {
            Main.getInstance().getLogger().log(
                    Level.SEVERE,
                    "Could not save faction data for " + uuid,
                    exception
            );
        }
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
        this.type = type;
        this.modified = true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.modified = true;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
        this.modified = true;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
        this.modified = true;
    }

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {
        this.lives = lives;
        this.modified = true;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
        this.modified = true;
    }

    public Location getHome() {
        return home == null ? null : home.clone();
    }

    public void setHome(Location home) {
        this.home = home == null ? null : home.clone();
        this.modified = true;
    }

    public boolean hasHome() {
        return home != null;
    }

    public boolean isModified() {
        return modified;
    }

    public Map<UUID, Integer> getMembers() {
        return members;
    }

    public void addMember(UUID member, int value) {
        Integer oldValue = members.put(member, value);

        if (oldValue == null || oldValue != value) {
            modified = true;
        }
    }

    public void removeMember(UUID member) {
        if (members.remove(member) != null) {
            modified = true;
        }
    }

    public boolean hasMember(UUID member) {
        return members.containsKey(member);
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

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
        modified = true;
    }

    public double getDTR() {
        return dtr;
    }

    public void setDTR(double dtr) {
        this.dtr = dtr;
        modified = true;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
        modified = true;
    }

    public int getKOTHCaptures() {
        return kothCaptures;
    }

    public void setKOTHCaptures(int kothCaptures) {
        this.kothCaptures = kothCaptures;
        modified = true;
    }

    public String getAnnouncement() {
        return announcement;
    }

    public void setAnnouncement(String announcement) {
        this.announcement = announcement;
        modified = true;
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

    public void disband() {
        for (UUID member : members.keySet()) {
            User user = Main.getInstance().getUser(member);
            user.setFaction(null);
            user.saveAsync();
        }
        Main.getInstance().removeFaction(uuid);

        String sql = "DELETE FROM factions WHERE uuid = ?";

        try (
                Connection connection =
                        Main.getInstance()
                                .getDatabaseManager()
                                .getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, uuid.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            Main.getInstance().getLogger().log(
                    Level.SEVERE,
                    "Could not delete faction " + uuid,
                    exception
            );
        }
    }

    public void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(
                Main.getInstance(),
                this::save
        );
    }

    public static boolean isNameInvalid(@NotNull String name, CommandSender p) {
        if (Main.getInstance().getFaction(name) != null) {
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
        for (Claim claim : Main.getInstance().getClaims()) {
            if (claim.faction().equals(uuid)) return true;
        }
        return false;
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
        this.deathban = deathban;
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
            return localeConfig.getString("faction.wilderness");
        }
        return localeConfig.getString("faction.warzone");
    }

    public boolean isClaiming() {
        return claiming;
    }

    public void setClaiming(boolean claiming) {
        this.claiming = claiming;
    }
}
