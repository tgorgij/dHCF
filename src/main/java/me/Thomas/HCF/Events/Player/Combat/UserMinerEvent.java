package me.Thomas.HCF.Events.Player.Combat;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UserMinerEvent implements Listener {

    private record SavedEffect(PotionEffect effect, long expires) {}

    private static class MinerState {
        private final long equippedAt = System.currentTimeMillis();
        private boolean active;
        private boolean underground;
        private long nextRefresh;
        private final Map<PotionEffectType, SavedEffect> effects = new HashMap<>();
    }

    private final Map<UUID, MinerState> miners = new HashMap<>();
    private final FileConfiguration config = Main.getInstance().getConfig();
    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();
    private final BukkitTask task;
    private boolean changingEffects;

    public UserMinerEvent() {
        task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            for (Player p : Bukkit.getOnlinePlayers()) update(p);
        }, 1L, 5L);
    }

    private boolean isIron(ItemStack item, Material material) {
        return item != null && item.getType() == material;
    }

    private boolean hasIron(Player p) {
        PlayerInventory inventory = p.getInventory();
        return isIron(inventory.getHelmet(), Material.IRON_HELMET)
                && isIron(inventory.getChestplate(), Material.IRON_CHESTPLATE)
                && isIron(inventory.getLeggings(), Material.IRON_LEGGINGS)
                && isIron(inventory.getBoots(), Material.IRON_BOOTS);
    }

    private MinerState update(Player p) { return update(p, p.getLocation().getY()); }

    private MinerState update(Player p, double y) {
        if (!p.isOnline() || p.isDead() || !hasIron(p) || User.get(p).isInStaffMode() || User.get(p).isVanished()
                || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
            deactivate(p, p.isOnline() && !p.isDead());
            return null;
        }
        MinerState state = miners.computeIfAbsent(p.getUniqueId(), ignored -> new MinerState());
        long now = System.currentTimeMillis();
        long activation = Math.clamp(config.getLong("classes.miner.activation", 0L), 0L, Integer.MAX_VALUE);
        if (!state.active && now - state.equippedAt >= activation * 1000L) {
            state.active = true;
            p.sendMessage(C.chat(localeConfig.getString("events.player.miner.activated")));
        }
        boolean underground = y < config.getInt("classes.miner.invisibility-below-y", 30);
        if (state.active && (now >= state.nextRefresh || underground != state.underground)) refresh(p, state, now, underground);
        return state;
    }

    private int duration(String path, int fallback) {
        return (int) Math.clamp(config.getLong(path + ".duration", fallback), 0L, Integer.MAX_VALUE / 20L) * 20;
    }

    private int strength(String path, int fallback) {
        return Math.clamp(config.getInt(path + ".strength", fallback), 0, 256);
    }

    private void refresh(Player p, MinerState state, long now, boolean underground) {
        applyEffect(p, state, PotionEffectType.HASTE, "haste");
        applyEffect(p, state, PotionEffectType.RESISTANCE, "resistance");
        applyEffect(p, state, PotionEffectType.FIRE_RESISTANCE, "fire_resistance");
        if (underground) applyEffect(p, state, PotionEffectType.INVISIBILITY, "invisibility");
        else removeEffect(p, state, PotionEffectType.INVISIBILITY);
        state.underground = underground;
        state.nextRefresh = now + 250L;
    }

    private void applyEffect(Player p, MinerState state, PotionEffectType type, String name) {
        String path = "classes.miner.effects." + name;
        int level = strength(path, 1), ticks = duration(path, 5);
        if (level <= 0 || ticks <= 0) {
            removeEffect(p, state, type);
            return;
        }
        PotionEffect current = p.getPotionEffect(type);
        boolean owned = state.effects.containsKey(type);
        if (current != null) {
            if (!owned && current.getAmplifier() >= level - 1) return;
            if (owned && current.getAmplifier() == level - 1 && current.getDuration() >= Math.max(1, ticks / 2)) return;
        }
        SavedEffect previous = state.effects.get(type);
        if (!owned) {
            long expires = current == null ? 0L : current.isInfinite() ? Long.MAX_VALUE
                    : System.currentTimeMillis() + current.getDuration() * 50L;
            previous = new SavedEffect(current, expires);
        }
        changingEffects = true;
        try {
            if (owned && current != null && current.getAmplifier() > level - 1) p.removePotionEffect(type);
            if (p.addPotionEffect(new PotionEffect(type, ticks, level - 1, true, false, true))) state.effects.put(type, previous);
        } finally {
            changingEffects = false;
        }
    }

    private void removeEffect(Player p, MinerState state, PotionEffectType type) {
        SavedEffect previous = state.effects.remove(type);
        if (previous == null) return;
        changingEffects = true;
        try {
            p.removePotionEffect(type);
            if (previous.effect() == null) return;
            long remaining = previous.expires() - System.currentTimeMillis();
            if (previous.effect().isInfinite()) p.addPotionEffect(previous.effect());
            else if (remaining > 0L) p.addPotionEffect(previous.effect().withDuration((int) ((remaining + 49L) / 50L)));
        } finally {
            changingEffects = false;
        }
    }

    private void deactivate(Player p, boolean notify) {
        MinerState state = miners.remove(p.getUniqueId());
        if (state == null) return;
        for (PotionEffectType type : List.copyOf(state.effects.keySet())) removeEffect(p, state, type);
        if (notify && state.active) p.sendMessage(C.chat(localeConfig.getString("events.player.miner.deactivated")));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffectChange(EntityPotionEffectEvent e) {
        if (changingEffects || !(e.getEntity() instanceof Player p)
                || e.getCause() == EntityPotionEffectEvent.Cause.EXPIRATION
                || e.getAction() == EntityPotionEffectEvent.Action.CHANGED && !e.isOverride()) return;
        MinerState state = miners.get(p.getUniqueId());
        if (state != null) state.effects.remove(e.getModifiedType());
    }

    @EventHandler
    public void onEquipmentChange(EntityEquipmentChangedEvent e) {
        if (e.getEntity() instanceof Player p) update(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        update(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        deactivate(e.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        deactivate(e.getEntity(), false);
    }

    @EventHandler
    public void onDisable(PluginDisableEvent e) {
        if (e.getPlugin() != Main.getInstance()) return;
        task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) deactivate(p, false);
        miners.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        int threshold = config.getInt("classes.miner.invisibility-below-y", 30);
        if ((e.getFrom().getY() < threshold) != (e.getTo().getY() < threshold)) update(e.getPlayer(), e.getTo().getY());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() != null) update(e.getPlayer(), e.getTo().getY());
    }
}
