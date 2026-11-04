package me.Thomas.HCF.IO.Conquest;

import me.Thomas.HCF.IO.Database.SaveSnapshot;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.ServerTimer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class Conquest {

    public enum Color { RED, YELLOW, GREEN, BLUE }
    public record Standing(UUID faction, int points) {}
    private record Candidate(Player player, UUID faction) {}
    private record Position(UUID world, int x, int y, int z) {
        private static Position from(Location location) {
            if (location.getWorld() == null) throw new IllegalArgumentException("different-world");
            return new Position(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
        private static Position read(String value) {
            if (value == null) return null;
            String[] parts = value.split(";", -1);
            if (parts.length != 4) throw new IllegalArgumentException("Invalid Conquest position");
            return new Position(UUID.fromString(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        }
        private String serialize() { return world + ";" + x + ";" + y + ";" + z; }
    }

    public static class Zone {
        private int interval;
        private Position pos1;
        private Position pos2;
        private ServerTimer timer;
        private UUID player;
        private UUID faction;

        private Zone(int interval) { this.interval = interval; }
        public int getInterval() { return interval; }
        public ServerTimer getTimer() { return timer; }
        public UUID getCapturingPlayer() { return player; }
        public long getRemainingTimeMillis() { return timer == null ? interval * 1000L : timer.getRemainingTimeMillis(); }
        public String getFormattedTime() { return C.formatTime(getRemainingTimeMillis()); }
        public boolean contains(Location location) {
            return pos1 != null && pos2 != null && location != null && location.getWorld() != null
                    && pos1.world().equals(location.getWorld().getUID())
                    && location.getBlockX() >= Math.min(pos1.x(), pos2.x()) && location.getBlockX() <= Math.max(pos1.x(), pos2.x())
                    && location.getBlockY() >= Math.min(pos1.y(), pos2.y()) && location.getBlockY() <= Math.max(pos1.y(), pos2.y())
                    && location.getBlockZ() >= Math.min(pos1.z(), pos2.z()) && location.getBlockZ() <= Math.max(pos1.z(), pos2.z());
        }
        private void reset(boolean running) {
            player = null;
            faction = null;
            timer = running ? new ServerTimer(ServerTimer.Type.CONQUEST, interval) : null;
            if (timer != null) timer.pause();
        }
    }

    private final Map<Color, Zone> zones = new EnumMap<>(Color.class);
    private final Map<UUID, Integer> scores = new HashMap<>();
    private long revision;
    private volatile long savedRevision;
    private boolean running;

    public Conquest() {
        int interval = Math.max(1, Main.getInstance().getConfig().getInt("conquest.default-interval", 30));
        for (Color color : Color.values()) zones.put(color, new Zone(interval));
    }
    public static Conquest get() { return Main.getInstance().getConquest(); }
    public String getName() { return "Conquest"; }
    public boolean isRunning() { return running; }
    public Zone getZone(Color color) { return zones.get(color); }
    public int getPoints(UUID uuid) { return scores.getOrDefault(uuid, 0); }
    public void setPoints(UUID uuid, int points) {
        if (!running) throw new IllegalArgumentException("not-running");
        Faction faction = Faction.get(uuid);
        if (faction == null || faction.getType() != Faction.Type.PLAYER) throw new IllegalArgumentException("invalid-faction");
        if (points < 0) throw new IllegalArgumentException("invalid-points");
        scores.put(uuid, points);
    }

    public static Color color(String name) {
        try { return Color.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("invalid-zone"); }
    }

    public void loadZone(ResultSet row) throws SQLException {
        Color color = color(row.getString("color"));
        Zone zone = zones.get(color);
        int interval = row.getInt("intervalSeconds");
        if (interval < 1) throw new IllegalArgumentException("Invalid Conquest interval");
        Position one = Position.read(row.getString("pos1")), two = Position.read(row.getString("pos2"));
        validate(color, one, two);
        zone.interval = interval;
        zone.pos1 = one;
        zone.pos2 = two;
    }

    public void setInterval(Color color, int seconds) {
        if (running) throw new IllegalArgumentException("cancel-before-edit");
        if (seconds < 1) throw new IllegalArgumentException("invalid-interval");
        zones.get(color).interval = seconds;
        revision++;
    }
    public void setPosition(Color color, boolean first, Location location) {
        if (running) throw new IllegalArgumentException("cancel-before-edit");
        Zone zone = zones.get(color);
        Position one = first ? Position.from(location) : zone.pos1;
        Position two = first ? zone.pos2 : Position.from(location);
        validate(color, one, two);
        zone.pos1 = one;
        zone.pos2 = two;
        revision++;
    }

    private void validate(Color color, Position one, Position two) {
        Position selected = one == null ? two : one;
        if (selected == null) return;
        World world = Bukkit.getWorld(selected.world());
        for (Position position : new Position[]{one, two}) {
            if (position == null) continue;
            if (!position.world().equals(selected.world())) throw new IllegalArgumentException("different-world");
            if (world != null && (position.y() < world.getMinHeight() || position.y() >= world.getMaxHeight())) throw new IllegalArgumentException("invalid-height");
        }
        for (Map.Entry<Color, Zone> entry : zones.entrySet()) {
            if (entry.getKey() == color) continue;
            Zone other = entry.getValue();
            Position anchor = other.pos1 == null ? other.pos2 : other.pos1;
            if (anchor != null && !anchor.world().equals(selected.world())) throw new IllegalArgumentException("different-world");
            if (one == null || two == null || other.pos1 == null || other.pos2 == null) continue;
            if (Math.min(one.x(), two.x()) <= Math.max(other.pos1.x(), other.pos2.x())
                    && Math.max(one.x(), two.x()) >= Math.min(other.pos1.x(), other.pos2.x())
                    && Math.min(one.y(), two.y()) <= Math.max(other.pos1.y(), other.pos2.y())
                    && Math.max(one.y(), two.y()) >= Math.min(other.pos1.y(), other.pos2.y())
                    && Math.min(one.z(), two.z()) <= Math.max(other.pos1.z(), other.pos2.z())
                    && Math.max(one.z(), two.z()) >= Math.min(other.pos1.z(), other.pos2.z())) throw new IllegalArgumentException("overlapping-zones");
        }
    }

    public void start() {
        if (!Main.getInstance().isFeatureEnabled("conquest")) throw new IllegalArgumentException("feature-disabled");
        if (running) throw new IllegalArgumentException("running");
        for (Map.Entry<Color, Zone> entry : zones.entrySet()) {
            Zone zone = entry.getValue();
            if (zone.pos1 == null || zone.pos2 == null || Bukkit.getWorld(zone.pos1.world()) == null
                    || Bukkit.getWorld(zone.pos2.world()) == null) throw new IllegalArgumentException("no-area");
            if (zone.interval <= 0) throw new IllegalArgumentException("invalid-interval");
            validate(entry.getKey(), zone.pos1, zone.pos2);
        }
        scores.clear();
        running = true;
        for (Zone zone : zones.values()) zone.reset(true);
        broadcast("started", "", 0);
    }
    public void cancel() {
        if (!running) return;
        running = false;
        for (Zone zone : zones.values()) zone.reset(false);
        broadcast("cancelled", "", 0);
    }

    public void checkCapture(Player p, Location destination) {
        for (Zone zone : zones.values()) {
            if (p.getUniqueId().equals(zone.player) && !zone.contains(destination)) zone.reset(running);
        }
    }
    public void removeFaction(UUID uuid) {
        scores.remove(uuid);
        for (Zone zone : zones.values()) {
            if (uuid.equals(zone.faction)) zone.reset(running);
        }
    }
    public void onDeath(User victim) {
        if (!running) return;
        for (Zone zone : zones.values()) {
            if (victim.getUUID().equals(zone.player)) zone.reset(true);
        }
        Faction team = victim.hasFaction() ? Faction.get(victim.getFaction()) : null;
        if (team == null || team.getType() != Faction.Type.PLAYER || !team.hasMember(victim.getUUID()) || !scores.containsKey(team.getUUID())) return;
        int penalty = Math.max(0, Main.getInstance().getConfig().getInt("conquest.death-penalty", 5));
        scores.put(team.getUUID(), Math.max(0, scores.get(team.getUUID()) - penalty));
    }

    private Candidate candidate(Player p) {
        if (!p.isOnline() || p.isDead() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return null;
        User user = User.get(p);
        if (user.isInStaffMode() || user.isVanished() || user.hasPvpProtection() || !user.hasFaction()) return null;
        Faction team = Faction.get(user.getFaction());
        if (team == null || team.getType() != Faction.Type.PLAYER || !team.hasMember(p.getUniqueId())) return null;
        return new Candidate(p, team.getUUID());
    }

    public void tick() {
        if (!running) return;
        scores.keySet().removeIf(uuid -> Faction.get(uuid) == null || Faction.get(uuid).getType() != Faction.Type.PLAYER);
        Map<UUID, Candidate> candidates = new LinkedHashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Candidate candidate = candidate(p);
            if (candidate != null) candidates.put(p.getUniqueId(), candidate);
        }
        for (Zone zone : zones.values()) {
            Candidate holder = zone.player == null ? null : candidates.get(zone.player);
            if (zone.player != null && (holder == null || !holder.faction().equals(zone.faction) || !zone.contains(holder.player().getLocation()))) {
                zone.reset(true);
                holder = null;
            }
            if (holder == null) {
                for (Candidate next : candidates.values()) {
                    if (!zone.contains(next.player().getLocation())) continue;
                    holder = next;
                    zone.player = next.player().getUniqueId();
                    zone.faction = next.faction();
                    zone.timer.pause();
                    break;
                }
            }
            if (holder == null || zone.timer.getRemainingTimeMillis() > 0) continue;
            int gain = Math.max(0, Main.getInstance().getConfig().getInt("conquest.capture-points", 1));
            int points = Math.clamp((long) getPoints(holder.faction()) + gain, 0, Integer.MAX_VALUE);
            scores.put(holder.faction(), points);
            if (points >= Math.max(1, Main.getInstance().getConfig().getInt("conquest.win-points", 100))) {
                finish(holder, points);
                return;
            }
            zone.reset(true);
            zone.player = holder.player().getUniqueId();
            zone.faction = holder.faction();
            zone.timer.pause();
        }
    }

    public List<Standing> getLeaders() {
        return scores.entrySet().stream().filter(entry -> Faction.get(entry.getKey()) != null)
                .sorted(Comparator.<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(entry -> Faction.get(entry.getKey()).getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(entry -> entry.getKey().toString()))
                .limit(3).map(entry -> new Standing(entry.getKey(), entry.getValue())).toList();
    }

    private void finish(Candidate winner, int points) {
        running = false;
        for (Zone zone : zones.values()) zone.reset(false);
        Faction team = Faction.get(winner.faction());
        int captures = Math.max(0, Main.getInstance().getConfig().getInt("conquest.event-captures", 5));
        team.setEventCaptures((int) Math.min(Integer.MAX_VALUE, (long) team.getEventCaptures() + captures));
        int gained = Math.max(0, Main.getInstance().getConfig().getInt("factions.points.conquest", 250));
        team.setPoints(Math.clamp((long) team.getPoints() + gained, Integer.MIN_VALUE, Integer.MAX_VALUE));
        team.saveAsync();
        broadcast("captured", team.getName(), points);
        for (String configured : Main.getInstance().getConfig().getStringList("conquest.rewards.commands")) {
            String command = configured.replace("%player%", winner.player().getName()).replace("%faction%", team.getName())
                    .replace("%conquest%", getName()).replace("%points%", Integer.toString(points));
            if (command.startsWith("/")) command = command.substring(1);
            if (command.isBlank()) continue;
            try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command); }
            catch (RuntimeException exception) { Main.getInstance().getLogger().log(Level.SEVERE, "Could not run Conquest reward: " + command, exception); }
        }
    }
    private void broadcast(String key, String team, int points) {
        Bukkit.broadcast(C.chat(Main.getInstance().getLocaleConfig().getString("events.conquest." + key)
                .replace("%conquest%", getName()).replace("%faction%", team).replace("%points%", Integer.toString(points))));
    }

    public List<SaveSnapshot> snapshot() {
        if (revision == savedRevision) return List.of();
        long version = revision;
        List<SaveSnapshot> snapshots = new ArrayList<>();
        Color[] colors = Color.values();
        for (int i = 0; i < colors.length; i++) {
            Color color = colors[i];
            Zone zone = zones.get(color);
            Runnable committed = i == colors.length - 1 ? () -> savedRevision = version : () -> {};
            snapshots.add(new SaveSnapshot("INSERT INTO conquestZones (color, intervalSeconds, pos1, pos2) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT(color) DO UPDATE SET intervalSeconds=excluded.intervalSeconds, pos1=excluded.pos1, pos2=excluded.pos2",
                    committed, color.name(), zone.interval, zone.pos1 == null ? null : zone.pos1.serialize(), zone.pos2 == null ? null : zone.pos2.serialize()));
        }
        return snapshots;
    }
    public void saveAsync() {
        List<SaveSnapshot> snapshots = snapshot();
        if (!snapshots.isEmpty()) Main.getInstance().getDatabaseManager().save(snapshots);
    }
}
