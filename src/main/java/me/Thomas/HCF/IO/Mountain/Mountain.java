package me.Thomas.HCF.IO.Mountain;

import me.Thomas.HCF.IO.Claims.Claim;
import me.Thomas.HCF.IO.Database.SaveSnapshot;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

public class Mountain {

    private record Position(UUID world, int x, int y, int z) {
        private static Position from(Location location) {
            if (location.getWorld() == null) throw new IllegalArgumentException("different-world");
            return new Position(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
        private static Position read(String value) {
            if (value == null) return null;
            String[] parts = value.split(";", -1);
            if (parts.length != 4) throw new IllegalArgumentException("Invalid mountain position");
            return new Position(UUID.fromString(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        }
        private String serialize() { return world + ";" + x + ";" + y + ";" + z; }
    }

    private final UUID uuid;
    private int interval;
    private Material block;
    private Position pos1;
    private Position pos2;
    private long nextRegeneration;
    private long revision;
    private volatile long savedRevision;
    private boolean regenerating;
    private boolean loading;
    private long generation;
    private long cursor;
    private long nextClaimCheck;
    private Chunk ticket;

    public Mountain(UUID uuid) {
        this.uuid = uuid;
        interval = Math.max(1, Main.getInstance().getConfig().getInt("mountains.default-interval", 30));
        block = Material.matchMaterial(Main.getInstance().getConfig().getString("mountains.default-block", "GLOWSTONE"));
        if (block == null || block.isLegacy() || !block.isBlock() || block.isAir()) block = Material.GLOWSTONE;
        revision++;
    }

    public Mountain(ResultSet row) throws SQLException {
        uuid = UUID.fromString(row.getString("faction"));
        interval = row.getInt("intervalMinutes");
        block = Material.matchMaterial(row.getString("block"));
        pos1 = Position.read(row.getString("pos1"));
        pos2 = Position.read(row.getString("pos2"));
        nextRegeneration = row.getLong("nextRegeneration");
        if (interval < 1 || block == null || block.isLegacy() || !block.isBlock() || block.isAir()) throw new IllegalArgumentException("Invalid mountain configuration");
        validate(pos1, pos2, false);
    }

    public static Mountain get(UUID uuid) { return Main.getInstance().getMountain(uuid); }
    public static Mountain get(String name) {
        Faction faction = Faction.get(name);
        return faction == null ? null : get(faction.getUUID());
    }
    public UUID getUUID() { return uuid; }
    public String getName() {
        Faction faction = Faction.get(uuid);
        return faction == null ? uuid.toString() : faction.getName();
    }
    public int getInterval() { return interval; }
    public Material getBlock() { return block; }
    public long getNextRegeneration() { return nextRegeneration; }
    public boolean isRegenerating() { return regenerating; }

    public void setInterval(int minutes) {
        if (minutes < 1) throw new IllegalArgumentException("invalid-interval");
        interval = minutes;
        reschedule();
    }
    public void setBlock(Material material) {
        if (material == null || material.isLegacy() || !material.isBlock() || material.isAir()) throw new IllegalArgumentException("invalid-block");
        block = material;
        reschedule();
    }
    public void setPos1(Location location) {
        Position position = Position.from(location);
        validate(position, pos2, true);
        pos1 = position;
        reschedule();
    }
    public void setPos2(Location location) {
        Position position = Position.from(location);
        validate(pos1, position, true);
        pos2 = position;
        reschedule();
    }

    private void validate(Position one, Position two, boolean claims) {
        if (one != null && two != null && !one.world().equals(two.world())) throw new IllegalArgumentException("different-world");
        Position selected = one == null ? two : one;
        if (selected == null) return;
        World world = Bukkit.getWorld(selected.world());
        for (Position position : new Position[]{one, two}) {
            if (position == null) continue;
            if (world != null && (position.y() < world.getMinHeight() || position.y() >= world.getMaxHeight())) throw new IllegalArgumentException("invalid-height");
            if (claims) {
                Faction faction = Faction.getAtLocation(new Location(world, position.x(), position.y(), position.z()));
                if (faction == null || !uuid.equals(faction.getUUID()) || faction.getType() != Faction.Type.MOUNTAIN) throw new IllegalArgumentException("outside-claim");
            }
        }
        if (one == null || two == null) return;
        long maximum = Math.clamp(Main.getInstance().getConfig().getLong("mountains.max-volume", 1_000_000L), 1L, Integer.MAX_VALUE);
        try {
            long volume = Math.multiplyExact(Math.multiplyExact(Math.abs((long) one.x() - two.x()) + 1L,
                    Math.abs((long) one.z() - two.z()) + 1L), Math.abs((long) one.y() - two.y()) + 1L);
            if (volume > maximum) throw new IllegalArgumentException("too-large");
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("too-large");
        }
        if (claims && !isClaimed(one, two)) throw new IllegalArgumentException("outside-claim");
    }

    private boolean isClaimed(Position one, Position two) {
        int minX = Math.min(one.x(), two.x()), maxX = Math.max(one.x(), two.x());
        int minZ = Math.min(one.z(), two.z()), maxZ = Math.max(one.z(), two.z());
        long covered = 0L;
        for (Claim claim : Main.getInstance().getClaims()) {
            if (!claim.faction().equals(uuid) || !claim.world().equals(one.world())) continue;
            long width = (long) Math.min(maxX, claim.maxX()) - Math.max(minX, claim.minX()) + 1L;
            long length = (long) Math.min(maxZ, claim.maxZ()) - Math.max(minZ, claim.minZ()) + 1L;
            if (width > 0 && length > 0) covered += width * length;
        }
        return covered == ((long) maxX - minX + 1L) * ((long) maxZ - minZ + 1L);
    }

    public boolean contains(Location location) {
        return pos1 != null && pos2 != null && location != null && location.getWorld() != null
                && location.getWorld().getUID().equals(pos1.world())
                && location.getBlockX() >= Math.min(pos1.x(), pos2.x()) && location.getBlockX() <= Math.max(pos1.x(), pos2.x())
                && location.getBlockY() >= Math.min(pos1.y(), pos2.y()) && location.getBlockY() <= Math.max(pos1.y(), pos2.y())
                && location.getBlockZ() >= Math.min(pos1.z(), pos2.z()) && location.getBlockZ() <= Math.max(pos1.z(), pos2.z());
    }

    public void reset() {
        if (pos1 == null || pos2 == null) throw new IllegalArgumentException("no-area");
        if (Bukkit.getWorld(pos1.world()) == null) throw new IllegalArgumentException("world-unavailable");
        validate(pos1, pos2, true);
        cancelRegeneration();
        nextClaimCheck = 0L;
        nextRegeneration = System.currentTimeMillis();
        revision++;
    }

    private void reschedule() {
        cancelRegeneration();
        nextClaimCheck = 0L;
        nextRegeneration = pos1 == null || pos2 == null ? 0L : System.currentTimeMillis() + interval * 60_000L;
        revision++;
    }

    public int tick(int budget) {
        Faction faction = Faction.get(uuid);
        if (faction == null || faction.getType() != Faction.Type.MOUNTAIN || pos1 == null || pos2 == null) {
            cancelRegeneration();
            return 0;
        }
        World world = Bukkit.getWorld(pos1.world());
        if (world == null) {
            cancelRegeneration();
            return 0;
        }
        if (budget <= 0 || loading) return 0;
        if (!regenerating) {
            long now = System.currentTimeMillis();
            if (nextRegeneration == 0L || now < nextRegeneration || now < nextClaimCheck) return 0;
            if (!isClaimed(pos1, pos2)) {
                nextClaimCheck = now + 5000L;
                return 0;
            }
            regenerating = true;
            cursor = 0L;
        }
        long height = Math.abs((long) pos1.y() - pos2.y()) + 1L;
        long width = Math.abs((long) pos1.x() - pos2.x()) + 1L;
        long volume = height * width * (Math.abs((long) pos1.z() - pos2.z()) + 1L);
        int used = 0;
        while (cursor < volume && used < budget) {
            int x = Math.min(pos1.x(), pos2.x()) + (int) (cursor / height % width);
            int y = Math.min(pos1.y(), pos2.y()) + (int) (cursor % height);
            int z = Math.min(pos1.z(), pos2.z()) + (int) (cursor / (height * width));
            if (ticket == null || ticket.getX() != x >> 4 || ticket.getZ() != z >> 4) {
                releaseTicket();
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    loadChunk(world, x >> 4, z >> 4);
                    return used;
                }
                ticket = world.getChunkAt(x >> 4, z >> 4);
                ticket.addPluginChunkTicket(Main.getInstance());
            }
            Location location = new Location(world, x, y, z);
            Faction current = Faction.getAtLocation(location);
            if (current != null && current.getUUID().equals(uuid) && current.getType() == Faction.Type.MOUNTAIN) {
                world.getBlockAt(x, y, z).setType(block, false);
            }
            cursor++;
            used++;
        }
        if (cursor == volume) {
            reschedule();
            saveAsync();
            String message = Main.getInstance().getLocaleConfig().getString("events.mountain.regenerated").replace("%mountain%", getName());
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (User.get(p).isMountainMessages()) p.sendMessage(C.chat(message));
            }
        }
        return used;
    }

    private void loadChunk(World world, int x, int z) {
        loading = true;
        long current = generation;
        world.getChunkAtAsync(x, z, true).whenComplete((chunk, failure) -> {
            if (!Main.getInstance().isEnabled()) return;
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                if (current != generation || !regenerating) return;
                loading = false;
                if (failure != null || chunk == null) {
                    Main.getInstance().getLogger().log(Level.WARNING, "Could not load mountain chunk for " + getName(), failure);
                    reschedule();
                    saveAsync();
                    return;
                }
                ticket = chunk;
                ticket.addPluginChunkTicket(Main.getInstance());
            });
        });
    }

    private void releaseTicket() {
        if (ticket == null) return;
        ticket.removePluginChunkTicket(Main.getInstance());
        ticket = null;
    }
    public void cancelRegeneration() {
        generation++;
        regenerating = false;
        loading = false;
        releaseTicket();
    }
    public SaveSnapshot snapshot() {
        if (revision == savedRevision) return null;
        long version = revision;
        return new SaveSnapshot("INSERT INTO mountains (faction, intervalMinutes, block, pos1, pos2, nextRegeneration) VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(faction) DO UPDATE SET intervalMinutes=excluded.intervalMinutes, block=excluded.block, "
                + "pos1=excluded.pos1, pos2=excluded.pos2, nextRegeneration=excluded.nextRegeneration", () -> savedRevision = version,
                uuid.toString(), interval, block.name(), pos1 == null ? null : pos1.serialize(), pos2 == null ? null : pos2.serialize(), nextRegeneration);
    }
    public void saveAsync() {
        SaveSnapshot snapshot = snapshot();
        if (snapshot != null) Main.getInstance().getDatabaseManager().save(java.util.List.of(snapshot));
    }
}
