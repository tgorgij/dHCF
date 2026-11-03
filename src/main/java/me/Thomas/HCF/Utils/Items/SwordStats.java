package me.Thomas.HCF.Utils.Items;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SwordStats {
    private static NamespacedKey key(String name) { return new NamespacedKey(Main.getInstance(), "sword-" + name); }

    public static void record(Player killer, Component deathMessage) {
        if (!Main.getInstance().getConfig().getBoolean("sword-stats.enabled", true)) return;
        ItemStack sword = killer.getInventory().getItemInMainHand();
        if (!sword.getType().name().endsWith("_SWORD")) return;
        ItemMeta meta = sword.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        int previous = Math.max(0, data.getOrDefault(key("kills"), PersistentDataType.INTEGER, 0));
        int kills = previous == Integer.MAX_VALUE ? previous : previous + 1;
        GsonComponentSerializer serializer = GsonComponentSerializer.gson();
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        List<String> old = data.getOrDefault(key("lore"), PersistentDataType.LIST.strings(), List.of());
        List<String> serializedLore = lore.stream().map(serializer::serialize).toList();
        int start = old.isEmpty() ? -1 : Collections.indexOfSubList(serializedLore, old);
        if (start >= 0) lore.subList(start, start + old.size()).clear();
        List<String> history = new ArrayList<>(data.getOrDefault(key("history"), PersistentDataType.LIST.strings(), List.of()));
        history.add(serializer.serialize(deathMessage));
        int maximum = Math.clamp(Main.getInstance().getConfig().getInt("sword-stats.max-history", 10), 0, 100);
        if (history.size() > maximum) history.subList(0, history.size() - maximum).clear();
        List<Component> stats = new ArrayList<>();
        stats.add(C.chat(Main.getInstance().getItemsConfig().getString("sword-stats.kills", "&eKills: &c%kills%")
                .replace("%kills%", Integer.toString(kills))).decoration(TextDecoration.ITALIC, false));
        for (String message : history) {
            stats.add(C.chat(Main.getInstance().getItemsConfig().getString("sword-stats.death", "&7%message%"))
                    .replaceText(builder -> builder.matchLiteral("%message%").replacement(serializer.deserialize(message)))
                    .decoration(TextDecoration.ITALIC, false));
        }
        lore.addAll(stats);
        meta.lore(lore);
        data.set(key("kills"), PersistentDataType.INTEGER, kills);
        data.set(key("history"), PersistentDataType.LIST.strings(), history);
        data.set(key("lore"), PersistentDataType.LIST.strings(), stats.stream().map(serializer::serialize).toList());
        sword.setItemMeta(meta);
    }
}
