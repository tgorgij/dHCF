package me.Thomas.HCF.Events.Player.Item;

import me.Thomas.HCF.Main.Main;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAutoSmelt(BlockDropItemEvent e) {
        if (!Main.getInstance().isFeatureEnabled("auto-smelt") || e.getPlayer().getGameMode() == GameMode.CREATIVE
                || e.getPlayer().getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) return;
        for (Item drop : e.getItems()) {
            ItemStack item = drop.getItemStack();
            Material result = switch (item.getType()) {
                case RAW_IRON -> Material.IRON_INGOT;
                case RAW_GOLD -> Material.GOLD_INGOT;
                case RAW_COPPER -> Material.COPPER_INGOT;
                case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
                default -> null;
            };
            if (result != null) drop.setItemStack(item.withType(result));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExperience(PlayerExpChangeEvent e) {
        if (e.getAmount() <= 0) return;
        double multiplier = Main.getInstance().getConfig().getDouble("settings.xp-multiplier", 1.0);
        if (!Double.isFinite(multiplier) || multiplier < 0) return;
        e.setAmount((int) Math.min(Integer.MAX_VALUE, Math.floor(e.getAmount() * multiplier)));
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
