package me.Thomas.HCF.Events.Player.Item;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Utils.Rewards.TechTree;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class UserEnchantEvent implements Listener {

    private int limit(Player p, Enchantment enchantment) {
        return TechTree.get() == null ? base(enchantment) : TechTree.get().enchantmentLimit(p, enchantment);
    }
    private int base(Enchantment enchantment) { return Main.getInstance().getConfig().getInt("enchantments." + enchantment.getKey().getKey(), -1); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent e) {
        if (Permission.has(e.getEnchanter(), "mapkit.bypass")) return;
        Map<Enchantment, Integer> authorized = new HashMap<>();
        Iterator<Map.Entry<Enchantment, Integer>> enchants = e.getEnchantsToAdd().entrySet().iterator();
        while (enchants.hasNext()) {
            Map.Entry<Enchantment, Integer> enchant = enchants.next();
            int limit = limit(e.getEnchanter(), enchant.getKey());
            if (limit >= 0 && enchant.getValue() > limit) {
                if (limit == 0) { enchants.remove(); continue; }
                enchant.setValue(limit);
            }
            if (base(enchant.getKey()) >= 0 && enchant.getValue() > base(enchant.getKey())) authorized.put(enchant.getKey(), enchant.getValue());
        }
        ItemMeta meta = e.getItem().getItemMeta();
        if (meta == null) return;
        TechTree.markEnchantments(meta, authorized);
        e.getItem().setItemMeta(meta);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent e) {
        if (!(e.getView().getPlayer() instanceof Player p) || Permission.has(p, "mapkit.bypass")) return;
        ItemStack result = e.getResult();
        if (result == null) return;
        ItemStack capped = result.clone();
        ItemMeta meta = capped.getItemMeta();
        if (meta == null) return;
        Map<Enchantment, Integer> authorized = new HashMap<>();
        for (Map.Entry<Enchantment, Integer> enchant : TechTree.enchants(meta).entrySet()) {
            Enchantment type = enchant.getKey();
            int inherited = Math.max(TechTree.producedEnchantment(e.getInventory().getItem(0), type), TechTree.producedEnchantment(e.getInventory().getItem(1), type));
            int limit = limit(p, type);
            if (limit >= 0) limit = Math.max(limit, inherited);
            int level = limit < 0 ? enchant.getValue() : Math.min(enchant.getValue(), limit);
            if (meta.hasEnchant(type)) {
                meta.removeEnchant(type);
                if (level > 0) meta.addEnchant(type, level, true);
            }
            if (meta instanceof EnchantmentStorageMeta book && book.hasStoredEnchant(type)) {
                book.removeStoredEnchant(type);
                if (level > 0) book.addStoredEnchant(type, level, true);
            }
            if (base(type) >= 0 && level > base(type)) authorized.put(type, level);
        }
        TechTree.markEnchantments(meta, authorized);
        capped.setItemMeta(meta);
        e.setResult(capped);
    }
}
