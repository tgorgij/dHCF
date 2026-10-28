package me.Thomas.HCF.Events.Player.Movement;

import me.Thomas.HCF.IO.Claims.ChunkKey;
import me.Thomas.HCF.IO.Claims.Claim;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class UserBorderEvent implements Listener {

    private record Position(int x, int y, int z) {
        private Location location(World world) { return new Location(world, x, y, z); }
    }
    private record View(UUID world, Set<Position> blocks, long sentAt) {}
    private static final int[][] DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<UUID, Location> lastAllowed = new HashMap<>();
    private final Map<UUID, Long> messages = new HashMap<>();
    private final BlockData glass;
    private final BukkitTask task;

    public UserBorderEvent() {
        Material material = Material.matchMaterial(Main.getInstance().getConfig()
                .getString("factions.border.material", "RED_STAINED_GLASS"));
        if (material == null || !material.isBlock() || material.isAir()) material = Material.RED_STAINED_GLASS;
        glass = material.createBlockData();
        task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                User.get(p).updateProtectionTimers(p.getLocation());
                Location safe = lastAllowed.get(p.getUniqueId());
                Faction faction = Faction.getAtLocation(p.getLocation());
                String blocked = reason(User.get(p), faction);
                if (!p.isDead() && blocked != null && safe != null
                        && reason(User.get(p), Faction.getAtLocation(safe)) == null) {
                    if (p.teleport(safe)) {
                        deny(p, faction, blocked);
                        continue;
                    }
                }
                update(p, p.getLocation());
            }
        }, 1L, 5L);
    }

    private String reason(User user, Faction faction) {
        if (faction == null) return null;
        if (faction.getType() == Faction.Type.SAFEZONE) return user.hasTimer(Timer.Type.COMBAT) ? "blocked-combat" : null;
        if (faction.getType() != Faction.Type.ROAD && user.hasPvpProtection()) return "blocked-protection";
        return null;
    }

    private void guard(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to == null || to.getWorld() == null) return;
        Player p = e.getPlayer();
        User user = User.get(p);
        Faction target = Faction.getAtLocation(to);
        String reason = reason(user, target);
        if (reason == null) return;
        Faction source = Faction.getAtLocation(e.getFrom());
        Location safe = lastAllowed.get(p.getUniqueId());
        // A timer granted while already inside a claim must still let its owner leave.
        if (source != null && source.getUUID().equals(target.getUUID())
                && (safe == null || reason(user, Faction.getAtLocation(safe)) != null)) return;
        e.setCancelled(true);
        deny(p, target, reason);
        update(p, e.getFrom());
    }

    private void deny(Player p, Faction faction, String reason) {
        long now = System.currentTimeMillis();
        if (now - messages.getOrDefault(p.getUniqueId(), 0L) < 1000L) return;
        messages.put(p.getUniqueId(), now);
        p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("faction.moving." + reason)
                .replace("%faction%", faction.getFormattedName(User.get(p)))));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoveCheck(PlayerMoveEvent e) {
        if (!(e instanceof PlayerTeleportEvent) && e.hasChangedPosition()) guard(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleportCheck(PlayerTeleportEvent e) {
        guard(e);
    }

    private Set<Claim> nearby(Location at) {
        Set<Claim> claims = new HashSet<>();
        for (int x = (at.getBlockX() - 2) >> 4; x <= (at.getBlockX() + 2) >> 4; x++) {
            for (int z = (at.getBlockZ() - 2) >> 4; z <= (at.getBlockZ() + 2) >> 4; z++) {
                claims.addAll(Main.getInstance().getClaimsByChunk(new ChunkKey(at.getWorld().getUID(), x, z)));
            }
        }
        return claims;
    }

    private boolean boundary(World world, UUID faction, int x, int z) {
        Faction current = Faction.getAtLocation(new Location(world, x, 0, z));
        if (current == null || !current.getUUID().equals(faction)) return false;
        for (int[] direction : DIRECTIONS) {
            Faction next = Faction.getAtLocation(new Location(world, x + direction[0], 0, z + direction[1]));
            if (next == null || !next.getUUID().equals(faction)) return true;
        }
        return false;
    }

    private Set<Position> blocks(Player p, Location at) {
        Set<Position> result = new LinkedHashSet<>();
        User user = User.get(p);
        if (!user.hasTimer(Timer.Type.COMBAT) && !user.hasPvpProtection()) return result;
        Faction current = Faction.getAtLocation(at);
        World world = at.getWorld();
        for (Claim claim : nearby(at).stream().sorted(Comparator.comparingLong(claim ->
                Math.abs((long) Math.clamp(at.getBlockX(), claim.minX(), claim.maxX()) - at.getBlockX())
                        + Math.abs((long) Math.clamp(at.getBlockZ(), claim.minZ(), claim.maxZ()) - at.getBlockZ()))).toList()) {
            Faction faction = Faction.get(claim.faction());
            if (reason(user, faction) == null || current != null && current.getUUID().equals(claim.faction())) continue;
            int x = Math.clamp(at.getBlockX(), claim.minX(), claim.maxX());
            int z = Math.clamp(at.getBlockZ(), claim.minZ(), claim.maxZ());
            if (Math.abs((long) x - at.getBlockX()) > 1 || Math.abs((long) z - at.getBlockZ()) > 1) continue;
            if (result.contains(new Position(x, at.getBlockY(), z)) || !boundary(world, claim.faction(), x, z)) continue;
            Set<Position> columns = new LinkedHashSet<>();
            columns.add(new Position(x, at.getBlockY(), z));
            // On a flat edge the neighbours form a line; at a corner they wrap into an L.
            for (int[] direction : DIRECTIONS) {
                int nextX = x + direction[0];
                int nextZ = z + direction[1];
                if (boundary(world, claim.faction(), nextX, nextZ)) columns.add(new Position(nextX, at.getBlockY(), nextZ));
                if (columns.size() == 3) break;
            }
            for (Position column : columns) {
                for (int y = at.getBlockY(); y < at.getBlockY() + 3; y++) {
                    if (y >= world.getMinHeight() && y < world.getMaxHeight()) result.add(new Position(column.x(), y, column.z()));
                }
            }
        }
        return result;
    }

    private void update(Player p, Location at) {
        if (!p.isOnline() || p.isDead() || !Objects.equals(p.getWorld(), at.getWorld())) { clear(p); return; }
        if (reason(User.get(p), Faction.getAtLocation(at)) == null) lastAllowed.put(p.getUniqueId(), at.clone());
        World world = at.getWorld();
        Set<Position> desired = blocks(p, at);
        // Never load chunks for a cosmetic barrier, or replace a real solid block on the client.
        desired.removeIf(pos -> !world.isChunkLoaded(pos.x() >> 4, pos.z() >> 4)
                || !world.getBlockAt(pos.x(), pos.y(), pos.z()).isPassable());
        View previous = views.get(p.getUniqueId());
        Set<Position> shown = previous != null && previous.world().equals(world.getUID()) ? previous.blocks() : Set.of();
        for (Position position : shown) {
            if (!desired.contains(position)) restore(p, world, position);
        }
        long now = System.currentTimeMillis();
        // A chunk packet or a real block update can overwrite client-only blocks while standing still.
        boolean refresh = previous == null || !previous.world().equals(world.getUID()) || now - previous.sentAt() >= 1000L;
        for (Position position : desired) {
            if (refresh || !shown.contains(position)) p.sendBlockChange(position.location(world), glass);
        }
        if (desired.isEmpty()) views.remove(p.getUniqueId());
        else views.put(p.getUniqueId(), new View(world.getUID(), desired, refresh ? now : previous.sentAt()));
    }

    private void restore(Player p, World world, Position position) {
        if (world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) {
            p.sendBlockChange(position.location(world), world.getBlockAt(position.x(), position.y(), position.z()).getBlockData());
        }
    }

    private void clear(Player p) {
        View view = views.remove(p.getUniqueId());
        if (view == null || !p.getWorld().getUID().equals(view.world())) return;
        for (Position position : view.blocks()) restore(p, p.getWorld(), position);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e instanceof PlayerTeleportEvent) return;
        if (e.getFrom().getBlockX() != e.getTo().getBlockX() || e.getFrom().getBlockY() != e.getTo().getBlockY()
                || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) update(e.getPlayer(), e.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        clear(e.getPlayer());
        refreshLater(e.getPlayer());
    }

    private void refreshLater(Player p) {
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            if (p.isOnline()) {
                User.get(p).updateProtectionTimers(p.getLocation());
                update(p, p.getLocation());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        User.get(e.getPlayer()).updateProtectionTimers(e.getPlayer().getLocation());
        update(e.getPlayer(), e.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        clear(e.getPlayer());
        lastAllowed.remove(e.getPlayer().getUniqueId());
        User.get(e.getPlayer()).updateProtectionTimers(e.getRespawnLocation());
        refreshLater(e.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        views.remove(e.getPlayer().getUniqueId());
        refreshLater(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        clear(e.getEntity());
        lastAllowed.remove(e.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        views.remove(e.getPlayer().getUniqueId());
        lastAllowed.remove(e.getPlayer().getUniqueId());
        messages.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDisable(PluginDisableEvent e) {
        if (e.getPlugin() != Main.getInstance()) return;
        task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) clear(p);
        lastAllowed.clear();
        messages.clear();
    }
}
