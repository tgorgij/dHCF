package me.Thomas.HCF.Utils.Items;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Items {

    public static ItemStack create(String key, Material fallback, Map<String, String> placeholders) {
        Material material = Material.matchMaterial(Main.getInstance().getItemsConfig().getString(key + ".material", fallback.name()));
        if (material == null || material.isAir() || !material.isItem()
                || (key.equals("spawner") || key.equals("experience-bottle") || key.equals("abilities.archer")) && material != fallback
                || key.equals("death-sign") && !material.name().endsWith("_SIGN")) material = fallback;
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            decorate(meta, key, placeholders);
        });
        return item;
    }

    public static void decorate(ItemMeta meta, String key, Map<String, String> placeholders) {
        String name = Main.getInstance().getItemsConfig().getString(key + ".name", "");
        for (Map.Entry<String, String> entry : placeholders.entrySet()) name = name.replace(entry.getKey(), entry.getValue());
        meta.displayName(C.chat(name).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : Main.getInstance().getItemsConfig().getStringList(key + ".lore")) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) line = line.replace(entry.getKey(), entry.getValue());
            lore.add(C.chat(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
    }
}
