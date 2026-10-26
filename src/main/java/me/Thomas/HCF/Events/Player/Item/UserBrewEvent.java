package me.Thomas.HCF.Events.Player.Item;

import me.Thomas.HCF.Main.Main;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

public class UserBrewEvent implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        for (ItemStack result : e.getResults()) {
            if (result == null || !(result.getItemMeta() instanceof PotionMeta meta)) continue;
            PotionType type = meta.getBasePotionType();
            if (type == null) continue;

            String name = type.getKey().getKey();
            boolean extended = name.startsWith("long_");
            if (extended) name = name.substring("long_".length());
            else if (name.startsWith("strong_")) name = name.substring("strong_".length());

            String path = "potions." + name;
            if (!Main.getInstance().getConfig().getBoolean(path + ".enabled", true)
                    || (extended && !Main.getInstance().getConfig().getBoolean(path + ".extended", true))) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
