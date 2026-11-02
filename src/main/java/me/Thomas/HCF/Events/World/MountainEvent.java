package me.Thomas.HCF.Events.World;

import me.Thomas.HCF.IO.Mountain.Mountain;
import me.Thomas.HCF.Main.Main;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public class MountainEvent implements Listener {
    private final BukkitTask task;
    private int next;

    public MountainEvent() {
        task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            List<Mountain> mountains = List.copyOf(Main.getInstance().getAllMountains().values());
            if (mountains.isEmpty()) return;
            int budget = Math.clamp(Main.getInstance().getConfig().getInt("mountains.blocks-per-tick", 500), 1, 10_000);
            int start = Math.floorMod(next++, mountains.size());
            for (int i = 0; i < mountains.size() && budget > 0; i++) budget -= mountains.get((start + i) % mountains.size()).tick(budget);
        }, 1L, 1L);
    }

    @EventHandler
    public void onDisable(PluginDisableEvent e) {
        if (e.getPlugin() != Main.getInstance()) return;
        task.cancel();
        for (Mountain mountain : Main.getInstance().getAllMountains().values()) mountain.cancelRegeneration();
    }
}
