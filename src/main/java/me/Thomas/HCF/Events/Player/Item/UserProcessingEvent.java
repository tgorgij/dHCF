package me.Thomas.HCF.Events.Player.Item;

import me.Thomas.HCF.Main.Main;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;

public class UserProcessingEvent implements Listener {

    private int duration(int ticks, String path) {
        double multiplier = Main.getInstance().getConfig().getDouble(path, 1.0);
        if (!Double.isFinite(multiplier) || multiplier <= 0.0) multiplier = 1.0;
        return (int) Math.clamp(Math.ceil(ticks / multiplier), 1.0, Integer.MAX_VALUE);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmelt(FurnaceStartSmeltEvent e) {
        e.setTotalCookTime(duration(e.getTotalCookTime(), "processing.furnace-speed-multiplier"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBrew(BrewingStartEvent e) {
        int ticks = duration(e.getBrewingTime(), "processing.brewing-speed-multiplier");
        e.setBrewingTime(ticks);
        e.setRecipeBrewTime(ticks);
    }
}
