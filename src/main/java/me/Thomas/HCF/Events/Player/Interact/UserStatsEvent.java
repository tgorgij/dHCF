package me.Thomas.HCF.Events.Player.Interact;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserStatsEvent implements Listener {

    private static final Map<String, List<Material>> ORES = Map.ofEntries(
            Map.entry("coal", List.of(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE)),
            Map.entry("copper", List.of(Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE)),
            Map.entry("iron", List.of(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE)),
            Map.entry("gold", List.of(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE)),
            Map.entry("redstone", List.of(Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE)),
            Map.entry("lapis", List.of(Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE)),
            Map.entry("diamond", List.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE)),
            Map.entry("emerald", List.of(Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE)),
            Map.entry("nether-quartz", List.of(Material.NETHER_QUARTZ_ORE)),
            Map.entry("nether-gold", List.of(Material.NETHER_GOLD_ORE)),
            Map.entry("ancient-debris", List.of(Material.ANCIENT_DEBRIS)));

    private static class Menu implements InventoryHolder {
        private Inventory inventory;
        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    public static void open(Player p, User target) {
        if (Main.getInstance().getStaffMode() != null && Main.getInstance().getStaffMode().isFrozen(p)) return;
        OfflinePlayer profile = Bukkit.getOfflinePlayer(target.getUUID());
        Map<String, String> values = new HashMap<>();
        values.put("%player%", target.getName());
        values.put("%kills%", Integer.toString(target.getKills()));
        values.put("%deaths%", Integer.toString(target.getDeaths()));
        long total = 0L;
        for (Map.Entry<String, List<Material>> ore : ORES.entrySet()) {
            long mined = 0L;
            for (Material material : ore.getValue()) mined += Math.max(0, profile.getStatistic(Statistic.MINE_BLOCK, material));
            values.put("%" + ore.getKey() + "%", Long.toString(mined));
            total += mined;
        }
        values.put("%total-ores%", Long.toString(total));
        int rows = Math.clamp(Main.getInstance().getGuiConfig().getInt("stats.rows", 3), 1, 6);
        Menu menu = new Menu();
        menu.inventory = Bukkit.createInventory(menu, rows * 9,
                C.chat(format(Main.getInstance().getGuiConfig().getString("stats.title", "&eStats: %player%"), values)));
        int row = rows / 2 * 9;
        menu.inventory.setItem(row + 2, item("kills", Material.DIAMOND_SWORD, values));
        menu.inventory.setItem(row + 4, item("deaths", Material.SKELETON_SKULL, values));
        menu.inventory.setItem(row + 6, item("ores", Material.DIAMOND_PICKAXE, values));
        p.openInventory(menu.inventory);
    }

    private static String format(String text, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) text = text.replace(entry.getKey(), entry.getValue());
        return text;
    }

    private static ItemStack item(String key, Material fallback, Map<String, String> values) {
        String path = "stats.items." + key;
        Material material = Material.matchMaterial(Main.getInstance().getGuiConfig().getString(path + ".material", fallback.name()));
        ItemStack item = new ItemStack(material == null || material.isAir() || !material.isItem() ? fallback : material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(C.chat(format(Main.getInstance().getGuiConfig().getString(path + ".name", key), values)).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : Main.getInstance().getGuiConfig().getStringList(path + ".lore")) {
            lore.add(C.chat(format(line, values)).decoration(TextDecoration.ITALIC, false));
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
