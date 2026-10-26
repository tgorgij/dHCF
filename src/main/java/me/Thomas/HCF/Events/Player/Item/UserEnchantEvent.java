package me.Thomas.HCF.Events.Player.Item;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Iterator;
import java.util.Map;

public class UserEnchantEvent implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        if (Permission.has(e.getEnchanter(), "mapkit.bypass")) return;

        Iterator<Map.Entry<Enchantment, Integer>> enchants = e.getEnchantsToAdd().entrySet().iterator();
        while (enchants.hasNext()) {
            Map.Entry<Enchantment, Integer> enchant = enchants.next();
            int limit = getLimit(enchant.getKey());
            if (limit < 0 || enchant.getValue() <= limit) continue;
            if (limit == 0) enchants.remove();
            else enchant.setValue(limit);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent e) {
        if (Permission.has(e.getView().getPlayer(), "mapkit.bypass")) return;
        ItemStack result = e.getResult();
        if (result == null) return;

        // Only change the output; the input items and their metadata stay intact.
        ItemStack capped = result.clone();
        ItemMeta meta = capped.getItemMeta();
        if (meta == null) return;
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> enchant : meta.getEnchants().entrySet()) {
            int limit = getLimit(enchant.getKey());
            if (limit < 0 || enchant.getValue() <= limit) continue;
            if (limit == 0) meta.removeEnchant(enchant.getKey());
            else meta.addEnchant(enchant.getKey(), limit, true);
            changed = true;
        }

        // Combining two enchanted books stores enchantments instead of applying them.
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            for (Map.Entry<Enchantment, Integer> enchant : storageMeta.getStoredEnchants().entrySet()) {
                int limit = getLimit(enchant.getKey());
                if (limit < 0 || enchant.getValue() <= limit) continue;
                if (limit == 0) storageMeta.removeStoredEnchant(enchant.getKey());
                else storageMeta.addStoredEnchant(enchant.getKey(), limit, true);
                changed = true;
            }
        }

        if (!changed) return;
        capped.setItemMeta(meta);
        e.setResult(capped);
    }

    private int getLimit(Enchantment enchantment) {
        return Main.getInstance().getConfig().getInt("enchantments." + enchantment.getKey().getKey(), -1);
    }
}
