package me.Thomas.HCF.IO.KOTH;

import me.Thomas.HCF.IO.Database.SaveSnapshot;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.ServerTimer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

public class KOTH {

    private record Position(UUID world, int x, int y, int z) {
        private static Position from(Location location) {
            return new Position(Objects.requireNonNull(location.getWorld()).getUID(),
                    location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        private static Position read(String value) {
            if (value == null) return null;
            String[] parts = value.split(";", -1);
            if (parts.length != 4) throw new IllegalArgumentException("Invalid KOTH position");
            return new Position(UUID.fromString(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        }

        private String serialize() {
            return world + ";" + x + ";" + y + ";" + z;
        }

        private Location location() {
            return new Location(Bukkit.getWorld(world), x, y, z);
        }
    }

    private final UUID uuid;
    private String name;
    private String color = "BLUE";
    private boolean pearlable = true;
    private UUID faction;
    private Position pos1;
    private Position pos2;
    private long revision;
    private volatile long savedRevision;

    private ServerTimer timer;
    private long duration;
    private UUID capturingPlayer;
    private String capturingName;
    private long nextAnnouncementSeconds;

    public KOTH(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        revision++;
    }

    public KOTH(ResultSet row) throws SQLException {
        uuid = UUID.fromString(row.getString("uuid"));
        name = row.getString("name");
        color = row.getString("color");
        pearlable = row.getBoolean("pearlable");
        String factionValue = row.getString("faction");
        faction = factionValue == null ? null : UUID.fromString(factionValue);
        pos1 = Position.read(row.getString("pos1"));
        pos2 = Position.read(row.getString("pos2"));
        if (pos1 != null && pos2 != null && !pos1.world().equals(pos2.world())) {
            throw new IllegalArgumentException("KOTH positions must be in the same world");
        }
    }

    public static KOTH get(String name) { return Main.getInstance().getKOTH(name); }
    public static KOTH get(UUID uuid) { return Main.getInstance().getKOTH(uuid); }
    public UUID getUUID() { return uuid; }
    public String getName() { return name; }
    public String getColor() { return color; }
    public boolean isPearlable() { return pearlable; }
    public UUID getFaction() { return faction; }
    public Location getPos1() { return pos1 == null ? null : pos1.location(); }
    public Location getPos2() { return pos2 == null ? null : pos2.location(); }
    public boolean isRunning() { return timer != null; }
    public ServerTimer getTimer() { return timer; }
    public UUID getCapturingPlayer() { return capturingPlayer; }
    public long getRemainingTimeMillis() { return timer == null ? 0L : timer.getRemainingTimeMillis(); }
    public String getFormattedTime() { return C.formatTime(getRemainingTimeMillis()); }

    public void setName(String name) { this.name = name; revision++; }
    public void setColor(String color) { this.color = color; revision++; }

    public void setPearlable(boolean pearlable) {
        if (faction == null) throw new IllegalStateException("Set a KOTH faction before changing pearlability");
        this.pearlable = pearlable;
        revision++;
    }

    public void setFaction(UUID faction) {
        this.faction = faction;
        if (faction == null) pearlable = true;
        revision++;
    }

    public void setPos1(Location location) {
        Position position = Position.from(location);
        if (pos2 != null && !pos2.world().equals(position.world())) throw new IllegalArgumentException("Different worlds");
        pos1 = position;
        revision++;
    }

    public void setPos2(Location location) {
        Position position = Position.from(location);
        if (pos1 != null && !pos1.world().equals(position.world())) throw new IllegalArgumentException("Different worlds");
        pos2 = position;
        revision++;
    }

    public boolean hasCaptureArea() {
        return pos1 != null && pos2 != null && pos1.world().equals(pos2.world()) && Bukkit.getWorld(pos1.world()) != null;
    }

    public boolean contains(Location location) {
        if (pos1 == null || pos2 == null || location == null || location.getWorld() == null
                || !pos1.world().equals(location.getWorld().getUID())) return false;
        return location.getBlockX() >= Math.min(pos1.x(), pos2.x()) && location.getBlockX() <= Math.max(pos1.x(), pos2.x())
                && location.getBlockY() >= Math.min(pos1.y(), pos2.y()) && location.getBlockY() <= Math.max(pos1.y(), pos2.y())
                && location.getBlockZ() >= Math.min(pos1.z(), pos2.z()) && location.getBlockZ() <= Math.max(pos1.z(), pos2.z());
    }

    public SaveSnapshot snapshot() {
        if (revision == savedRevision) return null;
        long version = revision;
        return new SaveSnapshot("""
                INSERT INTO koths (uuid, name, color, pearlable, faction, pos1, pos2) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, color=excluded.color,
                pearlable=excluded.pearlable, faction=excluded.faction, pos1=excluded.pos1, pos2=excluded.pos2
                """, () -> savedRevision = version, uuid.toString(), name, color, pearlable ? 1 : 0,
                faction == null ? null : faction.toString(), pos1 == null ? null : pos1.serialize(),
                pos2 == null ? null : pos2.serialize());
    }

    public void saveAsync() {
        SaveSnapshot snapshot = snapshot();
        if (snapshot != null) Main.getInstance().getDatabaseManager().save(List.of(snapshot));
    }

    public void start(long seconds) {
        if (isRunning() || !hasCaptureArea() || seconds <= 0 || seconds > Integer.MAX_VALUE) {
            throw new IllegalStateException("KOTH cannot start");
        }
        duration = seconds;
        timer = new ServerTimer(ServerTimer.Type.KOTH, duration);
        timer.pause();
        scheduleAnnouncement();
        broadcast("started", "");
    }

    public void extend(long seconds) {
        if (!isRunning() || seconds <= 0 || seconds > Integer.MAX_VALUE - duration) {
            throw new IllegalArgumentException("Invalid extension");
        }
        timer.addTime(seconds);
        duration += seconds;
        scheduleAnnouncement();
        broadcast("extended", "");
    }

    public void cancel() {
        if (!isRunning()) return;
        timer = null;
        capturingPlayer = null;
        capturingName = null;
        broadcast("cancelled", "");
    }

    public void resetCapture() {
        if (!isRunning() || capturingPlayer == null) return;
        String previousName = capturingName;
        capturingPlayer = null;
        capturingName = null;
        timer = new ServerTimer(ServerTimer.Type.KOTH, duration);
        timer.pause();
        scheduleAnnouncement();
        broadcast("reset", previousName);
    }

    private boolean canCapture(Player p) {
        return p != null && p.isOnline() && !p.isDead() && !User.get(p).isInStaffMode() && !User.get(p).isVanished() && !User.get(p).hasPvpProtection() && p.getGameMode() != GameMode.SPECTATOR
                && p.getGameMode() != GameMode.CREATIVE && contains(p.getLocation());
    }

    public void tick() {
        if (!isRunning()) return;
        Player p = capturingPlayer == null ? null : Bukkit.getPlayer(capturingPlayer);
        if (capturingPlayer != null && !canCapture(p)) resetCapture();
        if (capturingPlayer == null) {
            for (Player candidate : Bukkit.getOnlinePlayers()) {
                if (!canCapture(candidate)) continue;
                p = candidate;
                capturingPlayer = p.getUniqueId();
                capturingName = p.getName();
                timer.pause();
                broadcast("capturing", capturingName);
                break;
            }
        }
        if (capturingPlayer != null && timer.getRemainingTimeMillis() == 0L) {
            finish(Objects.requireNonNull(p));
            return;
        }
        long remainingSeconds = (getRemainingTimeMillis() + 999L) / 1000L;
        if (capturingPlayer != null && nextAnnouncementSeconds > 0 && remainingSeconds <= nextAnnouncementSeconds) {
            broadcast("remaining", "");
            scheduleAnnouncement();
        }
    }

    private void scheduleAnnouncement() {
        long seconds = (getRemainingTimeMillis() + 999L) / 1000L;
        long interval = seconds > 60L ? 60L : 10L;
        nextAnnouncementSeconds = Math.max(0L, (seconds - 1L) / interval * interval);
    }

    private void finish(Player p) {
        timer = null;
        capturingPlayer = null;
        capturingName = null;
        broadcast("captured", p.getName());
        User user = User.get(p);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction != null && faction.getType() == Faction.Type.PLAYER && faction.hasMember(p.getUniqueId())) {
            faction.setKOTHCaptures((int) Math.min(Integer.MAX_VALUE, (long) faction.getKOTHCaptures() + 1));
            faction.addPoints(Math.max(0, Main.getInstance().getConfig().getInt("factions.points.koth", 50)));
            faction.saveAsync();
        }
        for (String configured : Main.getInstance().getConfig().getStringList("koth.rewards.commands")) {
            String command = configured.replace("%player%", p.getName()).replace("%koth%", name);
            if (command.startsWith("/")) command = command.substring(1);
            if (command.isBlank()) continue;
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (RuntimeException exception) {
                Main.getInstance().getLogger().log(Level.SEVERE, "Could not run KOTH reward: " + command, exception);
            }
        }
    }

    private void broadcast(String message, String player) {
        String text = Objects.requireNonNull(Main.getInstance().getLocaleConfig().getString("events.koth." + message))
                .replace("%koth%", name).replace("%player%", player);
        if (!message.equals("reset")) {
            long seconds = (getRemainingTimeMillis() + 999L) / 1000L;
            String time = seconds < 60L ? seconds + " seconds" : C.formatTime(seconds * 1000L);
            text = text.replace("%time%", time);
        }
        Bukkit.broadcast(C.chat(text));
    }
}
