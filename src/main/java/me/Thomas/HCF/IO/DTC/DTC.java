package me.Thomas.HCF.IO.DTC;

import me.Thomas.HCF.IO.Database.SaveSnapshot;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class DTC {
    private UUID world;
    private int x, y, z;
    private String blockData;
    private int maxHealth;
    private int health;
    private boolean running;
    private long revision;
    private volatile long savedRevision;
    private final Map<UUID, Long> lastBreaks = new HashMap<>();

    public DTC() { maxHealth = Math.max(1, Main.getInstance().getConfig().getInt("dtc.default-health", 100)); }
    public static DTC get() { return Main.getInstance().getDTC(); }
    public boolean isRunning() { return running; }
    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }
    public UUID getWorld() { return world; }
    public boolean contains(Location location) {
        return world != null && location != null && location.getWorld() != null && world.equals(location.getWorld().getUID())
                && x == location.getBlockX() && y == location.getBlockY() && z == location.getBlockZ();
    }
    public boolean isCoreChunk(UUID world, int x, int z) {
        return this.world != null && this.world.equals(world) && this.x >> 4 == x && this.z >> 4 == z;
    }
    public void load(ResultSet row) throws SQLException {
        String world = row.getString("world");
        this.world = world == null ? null : UUID.fromString(world);
        x = row.getInt("x"); y = row.getInt("y"); z = row.getInt("z");
        blockData = row.getString("blockData");
        maxHealth = row.getInt("health");
        if (maxHealth <= 0 || this.world != null && blockData == null) throw new IllegalArgumentException("Invalid DTC setup");
    }
    public void setCore(Block block) {
        if (running) throw new IllegalArgumentException("cancel-before-edit");
        Faction territory = Faction.getAtLocation(block.getLocation());
        if (!block.getType().isSolid() || block.getType().getHardness() < 0 || block.getState() instanceof TileState
                || territory != null && Set.of(Faction.Type.PLAYER, Faction.Type.SAFEZONE, Faction.Type.MOUNTAIN).contains(territory.getType())) {
            throw new IllegalArgumentException("invalid-core");
        }
        restoreCore();
        world = block.getWorld().getUID();
        x = block.getX(); y = block.getY(); z = block.getZ();
        blockData = block.getBlockData().getAsString();
        revision++;
    }
    public void clearCore() {
        if (running) throw new IllegalArgumentException("cancel-before-edit");
        restoreCore();
        world = null;
        blockData = null;
        revision++;
    }
    public void setHealth(int health) {
        if (running) throw new IllegalArgumentException("cancel-before-edit");
        if (health <= 0) throw new IllegalArgumentException("invalid-health");
        maxHealth = health;
        revision++;
    }
    public void restoreCore() {
        World world = this.world == null ? null : Bukkit.getWorld(this.world);
        if (world != null && blockData != null) world.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData(blockData), false);
    }
    public void start() {
        if (!Main.getInstance().isFeatureEnabled("dtc")) throw new IllegalArgumentException("feature-disabled");
        if (running) throw new IllegalArgumentException("running");
        World world = this.world == null ? null : Bukkit.getWorld(this.world);
        if (world == null || blockData == null) throw new IllegalArgumentException("no-core");
        if (maxHealth <= 0) throw new IllegalArgumentException("invalid-health");
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) throw new IllegalArgumentException("invalid-core");
        BlockData data;
        try { data = Bukkit.createBlockData(blockData); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("invalid-core"); }
        Faction territory = Faction.getAtLocation(new Location(world, x, y, z));
        if (!data.getMaterial().isSolid() || data.getMaterial().getHardness() < 0
                || world.getBlockAt(x, y, z).getState() instanceof TileState
                || territory != null && Set.of(Faction.Type.PLAYER, Faction.Type.SAFEZONE, Faction.Type.MOUNTAIN).contains(territory.getType())) {
            throw new IllegalArgumentException("invalid-core");
        }
        restoreCore();
        health = maxHealth;
        lastBreaks.clear();
        running = true;
        broadcast("started", "", "");
    }
    public void cancel() {
        if (!running) return;
        running = false;
        lastBreaks.clear();
        restoreCore();
        broadcast("cancelled", "", "");
    }
    public boolean canDamage(Player p) {
        if (!running || !p.isOnline() || p.isDead() || p.getGameMode() != GameMode.SURVIVAL) return false;
        User user = User.get(p);
        if (user == null || user.isInStaffMode() || user.isVanished() || user.hasPvpProtection() || !user.hasFaction()) return false;
        Faction faction = Faction.get(user.getFaction());
        long cooldown = Math.max(0, Main.getInstance().getConfig().getLong("dtc.break-cooldown-millis", 1000));
        return faction != null && faction.getType() == Faction.Type.PLAYER && faction.hasMember(p.getUniqueId())
                && System.currentTimeMillis() - lastBreaks.getOrDefault(p.getUniqueId(), 0L) >= cooldown;
    }
    public boolean breakCore(Player p) {
        if (!canDamage(p)) return false;
        lastBreaks.put(p.getUniqueId(), System.currentTimeMillis());
        health--;
        if (health > 0) {
            if (Main.getInstance().getConfig().getIntegerList("dtc.announce-at").contains(health)) broadcast("remaining", "", "");
            return true;
        }
        running = false;
        lastBreaks.clear();
        Faction faction = Faction.get(User.get(p).getFaction());
        faction.addPoints(Math.max(0, Main.getInstance().getConfig().getInt("factions.points.dtc", 50)));
        faction.setEventCaptures((int) Math.min(Integer.MAX_VALUE, (long) faction.getEventCaptures()
                + Math.max(0, Main.getInstance().getConfig().getInt("dtc.event-captures", 1))));
        faction.saveAsync();
        broadcast("captured", p.getName(), faction.getName());
        for (String configured : Main.getInstance().getConfig().getStringList("dtc.rewards.commands")) {
            String command = configured.replace("%player%", p.getName()).replace("%faction%", faction.getName());
            if (command.startsWith("/")) command = command.substring(1);
            if (command.isBlank()) continue;
            try { Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command); }
            catch (RuntimeException exception) { Main.getInstance().getLogger().log(Level.SEVERE, "Could not run DTC reward: " + command, exception); }
        }
        return true;
    }
    private void broadcast(String key, String player, String faction) {
        Bukkit.broadcast(C.chat(Main.getInstance().getLocaleConfig().getString("events.dtc." + key)
                .replace("%player%", player).replace("%faction%", faction).replace("%health%", Integer.toString(health))
                .replace("%max-health%", Integer.toString(maxHealth))));
    }
    public SaveSnapshot snapshot() {
        if (revision == savedRevision) return null;
        long version = revision;
        return new SaveSnapshot("INSERT INTO dtc(id,world,x,y,z,blockData,health) VALUES(1,?,?,?,?,?,?) "
                + "ON CONFLICT(id) DO UPDATE SET world=excluded.world,x=excluded.x,y=excluded.y,z=excluded.z,blockData=excluded.blockData,health=excluded.health",
                () -> savedRevision = version, world == null ? null : world.toString(), x, y, z, blockData, maxHealth);
    }
    public void saveAsync() {
        SaveSnapshot snapshot = snapshot();
        if (snapshot != null) Main.getInstance().getDatabaseManager().save(List.of(snapshot));
    }
}
