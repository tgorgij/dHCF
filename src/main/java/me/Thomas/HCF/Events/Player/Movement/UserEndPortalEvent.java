package me.Thomas.HCF.Events.Player.Movement;

import me.Thomas.HCF.Main.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.world.PortalCreateEvent;

import java.util.UUID;

public class UserEndPortalEvent implements Listener {
    private Location destination(String name) {
        ConfigurationSection section = Main.getInstance().getConfig().getConfigurationSection("end." + name);
        if (section == null) return null;
        World world;
        try { world = Bukkit.getWorld(UUID.fromString(section.getString("world", ""))); }
        catch (IllegalArgumentException exception) { return null; }
        if (world == null || (world.getEnvironment() == World.Environment.THE_END) != name.equals("entrance")) return null;
        double x = section.getDouble("x", Double.NaN), y = section.getDouble("y", Double.NaN), z = section.getDouble("z", Double.NaN);
        float yaw = (float) section.getDouble("yaw", 0), pitch = (float) section.getDouble("pitch", 0);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || y < world.getMinHeight() || y >= world.getMaxHeight()) return null;
        return new Location(world, x, y, z, yaw, pitch);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (e.getCause() != TeleportCause.END_PORTAL || e.getFrom().getWorld() == null) return;
        Location destination = destination(e.getFrom().getWorld().getEnvironment() == World.Environment.THE_END ? "exit" : "entrance");
        if (destination == null) return;
        e.setTo(destination);
        e.setCanCreatePortal(false);
        e.setSearchRadius(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent e) {
        if (e.getRespawnReason() != PlayerRespawnEvent.RespawnReason.END_PORTAL) return;
        Location destination = destination("exit");
        if (destination != null) e.setRespawnLocation(destination);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlatform(PortalCreateEvent e) {
        if (e.getReason() != PortalCreateEvent.CreateReason.END_PLATFORM || !(e.getEntity() instanceof Player)) return;
        Location entrance = destination("entrance");
        if (entrance != null && entrance.getWorld().equals(e.getWorld())) e.setCancelled(true);
    }
}
