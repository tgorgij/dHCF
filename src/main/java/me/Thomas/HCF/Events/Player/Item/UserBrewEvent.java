package me.Thomas.HCF.Events.Player.Item;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

public class UserBrewEvent implements Listener {

    private boolean isAllowed(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof PotionMeta meta)) return true;
        PotionType type = meta.getBasePotionType();
        if (type == null) return true;
        String name = type.getKey().getKey();
        boolean extended = name.startsWith("long_");
        if (extended) name = name.substring("long_".length());
        else if (name.startsWith("strong_")) name = name.substring("strong_".length());
        String path = "potions." + name;
        return Main.getInstance().getConfig().getBoolean(path + ".enabled", true)
                && (!extended || Main.getInstance().getConfig().getBoolean(path + ".extended", true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        for (ItemStack result : e.getResults()) {
            if (isAllowed(result)) continue;
            e.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (Permission.has(e.getPlayer(), "potions.bypass") || isAllowed(e.getItem())) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.potion-disabled")));
    }
}
