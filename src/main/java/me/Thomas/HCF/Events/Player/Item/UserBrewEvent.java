package me.Thomas.HCF.Events.Player.Item;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Utils.Rewards.TechTree;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserBrewEvent implements Listener {
    private static NamespacedKey brewerKey() { return new NamespacedKey(Main.getInstance(), "tech-brewer"); }

    private boolean isAllowed(ItemStack item, Faction faction) {
        if (item == null || !(item.getItemMeta() instanceof PotionMeta meta)) return true;
        PotionType type = meta.getBasePotionType();
        if (type == null) return true;
        return allowedByMapKit(type) || TechTree.producedPotion(item)
                || TechTree.get() != null && TechTree.get().hasPotion(faction, type.getKey().getKey());
    }

    private boolean allowedByMapKit(PotionType type) {
        String path = "potions." + TechTree.potionName(type);
        String variant = type.getKey().getKey();
        return Main.getInstance().getConfig().getBoolean(path + ".enabled", true)
                && (!variant.startsWith("long_") || Main.getInstance().getConfig().getBoolean(path + ".extended", true))
                && (!variant.startsWith("strong_") || Main.getInstance().getConfig().getBoolean(path + ".upgraded", true));
    }

    private Faction brewer(BrewEvent e) {
        Faction territory = Faction.getAtLocation(e.getBlock().getLocation());
        if (territory != null && territory.getType() == Faction.Type.PLAYER) return territory;
        if (!(e.getBlock().getState() instanceof BrewingStand stand)) return null;
        String uuid = stand.getPersistentDataContainer().get(brewerKey(), PersistentDataType.STRING);
        if (uuid == null) return null;
        try { return Faction.get(UUID.fromString(uuid)); }
        catch (IllegalArgumentException exception) { return null; }
    }

    private void assign(BrewingStand stand, Player p) {
        Faction faction = TechTree.faction(p);
        if (faction == null) stand.getPersistentDataContainer().remove(brewerKey());
        else stand.getPersistentDataContainer().set(brewerKey(), PersistentDataType.STRING, faction.getUUID().toString());
        stand.update();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p && e.getInventory().getHolder() instanceof BrewingStand stand) assign(stand, p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlockPlaced().getState() instanceof BrewingStand stand) assign(stand, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        Faction faction = brewer(e);
        List<ItemStack> results = new ArrayList<>();
        for (int i = 0; i < e.getResults().size(); i++) {
            ItemStack result = e.getResults().get(i);
            if (result == null) { results.add(null); continue; }
            ItemStack produced = result.clone();
            ItemStack input = e.getContents().getItem(i);
            if (produced.getItemMeta() instanceof PotionMeta meta && meta.getBasePotionType() != null
                    && input != null && input.getItemMeta() instanceof PotionMeta original && original.getBasePotionType() != null) {
                if (original.getBasePotionType() != meta.getBasePotionType()) TechTree.clearPotion(produced);
                else if (TechTree.producedPotion(input)) TechTree.markPotion(produced);
            }
            if (!isAllowed(produced, faction)) { e.setCancelled(true); return; }
            if (produced.getItemMeta() instanceof PotionMeta meta && meta.getBasePotionType() != null
                    && !allowedByMapKit(meta.getBasePotionType())) {
                TechTree.markPotion(produced);
            }
            results.add(produced);
        }
        for (int i = 0; i < results.size(); i++) e.getResults().set(i, results.get(i));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (Permission.has(e.getPlayer(), "potions.bypass") || isAllowed(e.getItem(), null)) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.potion-disabled")));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerLaunch(PlayerLaunchProjectileEvent e) {
        if (!(e.getProjectile() instanceof ThrownPotion) || Permission.has(e.getPlayer(), "potions.bypass") || isAllowed(e.getItemStack(), null)) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.potion-disabled")));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof ThrownPotion potion) || isAllowed(potion.getItem(), null)
                || potion.getShooter() instanceof Player p && Permission.has(p, "potions.bypass")) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent e) {
        if (isAllowed(e.getPotion().getItem(), null) || e.getPotion().getShooter() instanceof Player p && Permission.has(p, "potions.bypass")) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        if (!isAllowed(e.getItem(), null)) e.setCancelled(true);
    }
}
