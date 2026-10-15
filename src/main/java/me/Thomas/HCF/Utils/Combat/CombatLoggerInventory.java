package me.Thomas.HCF.Utils.Combat;

import me.Thomas.HCF.Main.Main;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

public final class CombatLoggerInventory {
    private CombatLoggerInventory() {
    }

    public static int getHeldSlot(Villager villager) {
        return Math.clamp(villager.getPersistentDataContainer().getOrDefault(
                new NamespacedKey(Main.getInstance(), "held-slot"), PersistentDataType.INTEGER, 0), 0, 8);
    }

    public static ItemStack[] capture(Villager villager) {
        byte[] savedInventory = Objects.requireNonNull(villager.getPersistentDataContainer().get(
                new NamespacedKey(Main.getInstance(), "inventory"), PersistentDataType.BYTE_ARRAY));
        ItemStack[] contents = ItemStack.deserializeItemsFromBytes(savedInventory);
        EntityEquipment equipment = Objects.requireNonNull(villager.getEquipment());
        // Player slots 36-39 are armor (boots first); slot 40 is the offhand.
        System.arraycopy(equipment.getArmorContents(), 0, contents, 36, 4);
        contents[getHeldSlot(villager)] = equipment.getItemInMainHand();
        contents[40] = equipment.getItemInOffHand();
        return contents;
    }

    public static List<ItemStack> getDrops(ItemStack[] contents) {
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack item : contents) {
            if (item != null && !item.isEmpty()) drops.add(item.clone());
        }
        return drops;
    }
}
