package me.Thomas.HCF.Events.Player.Interact;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class UserMapKitEvent implements Listener {
    private static class Menu implements InventoryHolder {
        private Inventory inventory;
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    public static void open(Player p) {
        if (Main.getInstance().getStaffMode() != null && Main.getInstance().getStaffMode().isFrozen(p)) return;
        int rows = Math.clamp(Main.getInstance().getGuiConfig().getInt("mapkit.rows", 3), 1, 6);
        Menu menu = new Menu();
        menu.inventory = Bukkit.createInventory(menu, rows * 9, C.chat(Main.getInstance().getGuiConfig().getString("mapkit.title", "&eMapkit")));
        ItemStack background = item("background", Material.GRAY_STAINED_GLASS_PANE, false);
        for (int slot = 0; slot < menu.inventory.getSize(); slot++) menu.inventory.setItem(slot, background);
        org.bukkit.configuration.ConfigurationSection items = Main.getInstance().getGuiConfig().getConfigurationSection("mapkit.items");
        if (items != null) for (String key : items.getKeys(false)) {
            int slot = items.getInt(key + ".slot", -1);
            if (slot < 0 || slot >= menu.inventory.getSize()) continue;
            menu.inventory.setItem(slot, item("items." + key, Material.BOOK, true));
        }
        p.openInventory(menu.inventory);
    }

    private static ItemStack item(String key, Material fallback, boolean enchantments) {
        String path = "mapkit." + key;
        Material material = Material.matchMaterial(Main.getInstance().getGuiConfig().getString(path + ".material", fallback.name()));
        ItemStack item = new ItemStack(material == null || material.isAir() || !material.isItem() ? fallback : material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(C.chat(Main.getInstance().getGuiConfig().getString(path + ".name", " ")).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : Main.getInstance().getGuiConfig().getStringList(path + ".lore")) {
            lore.add(C.chat(line).decoration(TextDecoration.ITALIC, false));
        }
        if (enchantments) {
            for (Enchantment enchantment : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)) {
                int configured = Main.getInstance().getConfig().getInt("enchantments." + enchantment.getKey().getKey(), -1);
                int limit = configured < 0 ? enchantment.getMaxLevel() : configured;
                if (limit == 0 || !enchantment.canEnchantItem(item)) continue;
                Component line = C.chat(Main.getInstance().getGuiConfig().getString("mapkit.enchantment", "&e%enchantment% &7%level%")
                        .replace("%level%", Integer.toString(limit)))
                        .replaceText(builder -> builder.matchLiteral("%enchantment%").replacement(enchantment.description()));
                lore.add(line.decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
