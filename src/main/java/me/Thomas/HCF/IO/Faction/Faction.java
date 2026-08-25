package me.Thomas.HCF.IO.Faction;

import me.Thomas.HCF.Main.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
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


    private boolean modified;

    private Set<String> claims = new HashSet<>();
    private Map<UUID, Integer> members = new HashMap<>();

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
        this.claims = new HashSet<>();
        this.members = new HashMap<>();
        this.modified = true;
        this.open = false;
        this.dtr = 0;
        this.points = 0;
        this.kothCaptures = 0;
        this.announcement = null;

        save();
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
        this.claims = new HashSet<>();
        this.members = new HashMap<>();
        this.members.put(leader, MemberType.LEADER.getValue());
        this.modified = true;
        this.open = false;
        this.dtr = 1.01;
        this.points = 0;
        this.kothCaptures = 0;
        this.announcement = null;

        save();
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
                    save();
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

                this.claims = deserializeClaims(
                        resultSet.getString("claims")
                );

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

                this.modified = false;
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
        this.claims = new HashSet<>();
        this.members = new HashMap<>();
        this.modified = true;
    }

    public void save() {
        if (!modified) {
            return;
        }

        String sql =
                "INSERT OR REPLACE INTO factions "
                        + "(uuid, type, name, leader, balance, lives, claims, "
                        + "members, color, home, open, dtr, points, kothCaptures, announcement) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String claimsValue = serializeClaims();
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
            statement.setString(7, claimsValue);
            statement.setString(8, membersValue);
            statement.setString(9, color);

            if (homeValue == null) {
                statement.setNull(10, Types.VARCHAR);
            } else {
                statement.setString(10, homeValue);
            }

            statement.setBoolean(11, open);
            statement.setDouble(12, dtr);
            statement.setInt(13, points);
            statement.setInt(14, kothCaptures);
            statement.setString(15, announcement == null ? null : announcement);

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

    private String serializeClaims() {
        StringBuilder result = new StringBuilder();

        for (String claim : claims) {
            if (!result.isEmpty()) {
                result.append(';');
            }

            result.append(claim);
        }

        return result.toString();
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

    public Set<String> getClaims() {
        return claims;
    }

    public void addClaim(String claim) {
        if (claims.add(claim)) {
            modified = true;
        }
    }

    public void removeClaim(String claim) {
        if (claims.remove(claim)) {
            modified = true;
        }
    }

    public boolean hasClaim(String claim) {
        return claims.contains(claim);
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

    private Set<String> deserializeClaims(String serialized) {
        Set<String> result = new HashSet<>();

        if (serialized == null || serialized.isEmpty()) {
            return result;
        }

        for (String claim : serialized.split(";")) {
            if (!claim.isEmpty()) {
                result.add(claim);
            }
        }

        return result;
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
}