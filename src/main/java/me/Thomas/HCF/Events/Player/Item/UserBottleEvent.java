package me.Thomas.HCF.Events.Player.Item;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class UserBottleEvent implements Listener {

    private static NamespacedKey experienceKey() { return new NamespacedKey(Main.getInstance(), "bottled-experience"); }
    private static NamespacedKey pendingKey() { return new NamespacedKey(Main.getInstance(), "bottled-experience-pending"); }

    private static int getExperience(ItemStack item) {
        if (item == null || item.getType() != Material.EXPERIENCE_BOTTLE || !item.hasItemMeta()) return 0;
        return Math.max(0, item.getItemMeta().getPersistentDataContainer().getOrDefault(experienceKey(), PersistentDataType.INTEGER, 0));
    }

    public static ItemStack createBottle(int experience) {
        if (experience <= 0) throw new IllegalArgumentException("Experience must be positive");
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(C.chat(Main.getInstance().getLocaleConfig().getString("commands.bottle.item-name"))
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : Main.getInstance().getLocaleConfig().getStringList("commands.bottle.item-lore")) {
            lore.add(C.chat(line.replace("%experience%", Integer.toString(experience))).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(experienceKey(), PersistentDataType.INTEGER, experience);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        if (getExperience(e.getItem()) > 0) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerLaunch(PlayerLaunchProjectileEvent e) {
        if (!(e.getProjectile() instanceof ThrownExpBottle bottle)) return;
        int experience = getExperience(e.getItemStack());
        if (experience <= 0) return;
        // Consume explicitly only once the projectile passes every launch check, including in creative mode.
        e.setShouldConsume(false);
        bottle.getPersistentDataContainer().set(pendingKey(), PersistentDataType.INTEGER, experience);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof ThrownExpBottle bottle) || !(bottle.getShooter() instanceof Player p)) return;
        Integer experience = bottle.getPersistentDataContainer().get(pendingKey(), PersistentDataType.INTEGER);
        if (experience == null) return;
        bottle.getPersistentDataContainer().remove(pendingKey());
        int stored = 0;
        ItemStack main = p.getInventory().getItemInMainHand();
        ItemStack off = p.getInventory().getItemInOffHand();
        if (getExperience(main) == experience && main.getAmount() > 0) {
            ItemStack remaining = main.clone();
            remaining.setAmount(main.getAmount() - 1);
            p.getInventory().setItemInMainHand(remaining.getAmount() == 0 ? null : remaining);
            stored = experience;
        } else if (getExperience(off) == experience && off.getAmount() > 0) {
            ItemStack remaining = off.clone();
            remaining.setAmount(off.getAmount() - 1);
            p.getInventory().setItemInOffHand(remaining.getAmount() == 0 ? null : remaining);
            stored = experience;
        }
        bottle.getPersistentDataContainer().set(experienceKey(), PersistentDataType.INTEGER, stored);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSplash(ExpBottleEvent e) {
        ThrownExpBottle bottle = e.getEntity();
        Integer stored = bottle.getPersistentDataContainer().get(experienceKey(), PersistentDataType.INTEGER);
        if (stored == null && getExperience(bottle.getItem()) <= 0) return;
        e.setExperience(0);
        if (stored == null || stored <= 0) return;
        // Clear the payload before awarding it, so repeat impact callbacks cannot redeem it twice.
        bottle.getPersistentDataContainer().set(experienceKey(), PersistentDataType.INTEGER, 0);
        Player p = bottle.getShooter() instanceof Player shooter ? Bukkit.getPlayer(shooter.getUniqueId()) : null;
        if (p == null || !p.isOnline() || p.isDead()) {
            bottle.getWorld().dropItemNaturally(bottle.getLocation(), createBottle(stored));
            return;
        }
        int current = p.calculateTotalExperiencePoints();
        long total = (long) current + stored;
        if (current < 0 || total > Integer.MAX_VALUE) {
            for (ItemStack leftover : p.getInventory().addItem(createBottle(stored)).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
            }
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.bottle.too-much-experience")));
            return;
        }
        p.setExperienceLevelAndProgress((int) total);
        p.setTotalExperience((int) total);
        p.saveData();
        p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.bottle.redeemed")
                .replace("%experience%", Integer.toString(stored))));
    }
}
