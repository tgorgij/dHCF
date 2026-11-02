package me.Thomas.HCF.Events.Player.Item;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UserSpawnerEvent implements Listener {

    private static NamespacedKey usesKey() { return new NamespacedKey(Main.getInstance(), "crowbar-uses"); }

    public static int getUses(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        return Math.max(0, item.getItemMeta().getPersistentDataContainer().getOrDefault(usesKey(), PersistentDataType.INTEGER, 0));
    }

    public static EntityType getType(String name) {
        try {
            EntityType type = EntityType.valueOf(name.toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""));
            return type.isAlive() && type.isSpawnable() && type != EntityType.ARMOR_STAND ? type : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static ItemStack createCrowbar(int uses) {
        if (uses <= 0) throw new IllegalArgumentException("Uses must be positive");
        ItemStack item = new ItemStack(Material.GOLDEN_HOE);
        updateCrowbar(item, uses);
        return item;
    }

    private static void updateCrowbar(ItemStack item, int uses) {
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(usesKey(), PersistentDataType.INTEGER, uses);
        decorate(meta, "crowbar", "%uses%", Integer.toString(uses));
        item.setItemMeta(meta);
    }

    public static ItemStack createSpawner(EntityType type) {
        if (type == null || getType(type.name()) == null) throw new IllegalArgumentException("Invalid spawner type");
        ItemStack item = new ItemStack(Material.SPAWNER);
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        CreatureSpawner spawner = (CreatureSpawner) meta.getBlockState();
        spawner.setSpawnedType(type);
        meta.setBlockState(spawner);
        decorate(meta, "spawner", "%type%", type.name().toLowerCase(Locale.ROOT).replace('_', ' '));
        item.setItemMeta(meta);
        return item;
    }

    private static void decorate(ItemMeta meta, String item, String placeholder, String value) {
        String path = "items." + item;
        meta.displayName(C.chat(Main.getInstance().getLocaleConfig().getString(path + ".name")
                .replace(placeholder, value)).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : Main.getInstance().getLocaleConfig().getStringList(path + ".lore")) {
            lore.add(C.chat(line.replace(placeholder, value)).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
    }

    public static boolean give(Player p, ItemStack item, int amount) {
        if (amount <= 0) return false;
        long capacity = 0;
        for (ItemStack current : p.getInventory().getStorageContents()) {
            if (current == null || current.isEmpty()) capacity += item.getMaxStackSize();
            else if (current.isSimilar(item)) capacity += Math.max(0, item.getMaxStackSize() - current.getAmount());
        }
        if (capacity < amount) return false;
        while (amount > 0) {
            ItemStack stack = item.clone();
            int count = Math.min(amount, item.getMaxStackSize());
            stack.setAmount(count);
            p.getInventory().addItem(stack);
            amount -= count;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        int uses = getUses(e.getPlayer().getInventory().getItemInMainHand());
        if (e.getBlock().getType() != Material.SPAWNER) {
            if (uses > 0) e.setCancelled(true);
            return;
        }
        if (uses == 0) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.spawner.crowbar-required")));
            return;
        }
        if (!(e.getBlock().getState() instanceof CreatureSpawner spawner) || spawner.getSpawnedType() == null
                || getType(spawner.getSpawnedType().name()) == null) {
            e.setCancelled(true);
            return;
        }
        e.setDropItems(false);
        e.setExpToDrop(0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBroken(BlockBreakEvent e) {
        if (e.getBlock().getType() != Material.SPAWNER || !(e.getBlock().getState() instanceof CreatureSpawner spawner)) return;
        Player p = e.getPlayer();
        ItemStack crowbar = p.getInventory().getItemInMainHand();
        int uses = getUses(crowbar);
        if (uses <= 0 || spawner.getSpawnedType() == null || getType(spawner.getSpawnedType().name()) == null) return;
        ItemStack drop = createSpawner(spawner.getSpawnedType());
        if (uses == 1) p.getInventory().setItemInMainHand(null);
        else {
            updateCrowbar(crowbar, uses - 1);
            p.getInventory().setItemInMainHand(crowbar);
        }
        e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(0.5, 0.5, 0.5), drop);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (getUses(e.getItem()) > 0) e.setUseItemInHand(Event.Result.DENY);
    }
}
