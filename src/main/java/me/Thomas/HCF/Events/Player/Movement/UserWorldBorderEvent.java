package me.Thomas.HCF.Events.Player.Movement;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class UserWorldBorderEvent implements Listener {
    private final Map<UUID, Long> lastMessage = new HashMap<>();

    public UserWorldBorderEvent() {
        for (World world : Bukkit.getWorlds()) apply(world);
    }

    public static double radius(World.Environment environment) {
        if (environment == World.Environment.THE_END || environment == World.Environment.CUSTOM) return Double.POSITIVE_INFINITY;
        double fallback = environment == World.Environment.NETHER ? 2000.0 : 3000.0;
        String name = environment == World.Environment.NETHER ? "nether" : "overworld";
        double radius = Main.getInstance().getConfig().getDouble("borders." + name, fallback);
        return Double.isFinite(radius) && radius >= 1.0 && radius <= 29_999_984.0 ? radius : fallback;
    }

    public static void apply(World world) {
        double radius = radius(world.getEnvironment());
        if (!Double.isFinite(radius)) return;
        world.getWorldBorder().setCenter(0, 0);
        world.getWorldBorder().setSize(radius * 2.0);
    }

    public static boolean isInside(Location location) {
        if (location == null || location.getWorld() == null) return false;
        double radius = radius(location.getWorld().getEnvironment());
        return Math.abs(location.getX()) < radius && Math.abs(location.getZ()) < radius;
    }

    private double distanceOutside(Location location) {
        double radius = radius(location.getWorld().getEnvironment());
        return Math.max(0.0, Math.abs(location.getX()) - radius) + Math.max(0.0, Math.abs(location.getZ()) - radius);
    }

    private void reject(Player player) {
        long now = System.currentTimeMillis();
        if (now - lastMessage.getOrDefault(player.getUniqueId(), 0L) < 1000L) return;
        lastMessage.put(player.getUniqueId(), now);
        player.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.world-border")));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedPosition() || isInside(e.getTo())) return;
        // Players outside a newly reduced border can still walk back inside it.
        if (e.getFrom().getWorld().equals(e.getTo().getWorld()) && !isInside(e.getFrom())
                && distanceOutside(e.getTo()) < distanceOutside(e.getFrom())) return;
        e.setCancelled(true);
        reject(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (isInside(e.getTo())) return;
        e.setCancelled(true);
        reject(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) { onTeleport(e); }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) { apply(e.getWorld()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { lastMessage.remove(e.getPlayer().getUniqueId()); }
}
