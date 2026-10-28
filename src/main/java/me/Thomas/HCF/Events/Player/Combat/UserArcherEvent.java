package me.Thomas.HCF.Events.Player.Combat;

import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UserArcherEvent implements Listener {

    private record SavedEffect(PotionEffect effect, long expires) {}

    private static class ArcherState {
        private final long equippedAt = System.currentTimeMillis();
        private boolean active;
        private long speedUntil;
        private long jumpUntil;
        private long nextRefresh;
        private final Map<PotionEffectType, SavedEffect> effects = new HashMap<>();
    }

    private final Map<UUID, ArcherState> archers = new HashMap<>();
    private final FileConfiguration config = Main.getInstance().getConfig();
    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();
    private final NamespacedKey shotKey = new NamespacedKey(Main.getInstance(), "archer-shot");
    private final BukkitTask task;
    private boolean changingEffects;

    public UserArcherEvent() {
        task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            for (Player p : Bukkit.getOnlinePlayers()) update(p);
        }, 1L, 1L);
    }

    private boolean isLeather(ItemStack item, Material material) {
        return item != null && item.getType() == material;
    }

    private boolean hasLeather(Player p) {
        PlayerInventory inventory = p.getInventory();
        return isLeather(inventory.getHelmet(), Material.LEATHER_HELMET)
                && isLeather(inventory.getChestplate(), Material.LEATHER_CHESTPLATE)
                && isLeather(inventory.getLeggings(), Material.LEATHER_LEGGINGS)
                && isLeather(inventory.getBoots(), Material.LEATHER_BOOTS);
    }

    private ArcherState update(Player p) {
        if (!p.isOnline() || p.isDead() || !hasLeather(p)) {
            deactivate(p, p.isOnline() && !p.isDead());
            return null;
        }
        ArcherState state = archers.computeIfAbsent(p.getUniqueId(), ignored -> new ArcherState());
        long now = System.currentTimeMillis();
        long activation = Math.clamp(config.getLong("classes.archer.activation", 0L), 0L, Integer.MAX_VALUE);
        if (!state.active && now - state.equippedAt >= activation * 1000L) {
            state.active = true;
            p.sendMessage(C.chat(localeConfig.getString("events.player.archer.activated")));
        }
        boolean speedEnded = state.speedUntil > 0L && now >= state.speedUntil;
        boolean jumpEnded = state.jumpUntil > 0L && now >= state.jumpUntil;
        if (speedEnded) state.speedUntil = 0L;
        if (jumpEnded) state.jumpUntil = 0L;
        if (state.active && (now >= state.nextRefresh || speedEnded || jumpEnded)) refresh(p, state, now);
        return state;
    }

    private int duration(String path, int fallback) {
        return (int) Math.clamp(config.getLong(path + ".duration", fallback), 0L, Integer.MAX_VALUE / 20L) * 20;
    }

    private int strength(String path, int fallback) {
        return Math.clamp(config.getInt(path + ".strength", fallback), 0, 256);
    }

    private void refresh(Player p, ArcherState state, long now) {
        String speed = state.speedUntil > now ? "classes.archer.abilities.speed" : "classes.archer.effects.speed";
        int speedTicks = state.speedUntil > now ? (int) ((state.speedUntil - now + 49L) / 50L) : duration(speed, 5);
        applyEffect(p, state, PotionEffectType.SPEED, strength(speed, state.speedUntil > now ? 3 : 2), speedTicks);
        applyEffect(p, state, PotionEffectType.RESISTANCE, strength("classes.archer.effects.resistance", 1),
                duration("classes.archer.effects.resistance", 5));
        if (state.jumpUntil > now) {
            applyEffect(p, state, PotionEffectType.JUMP_BOOST, strength("classes.archer.abilities.jump", 4),
                    (int) ((state.jumpUntil - now + 49L) / 50L));
        } else {
            removeEffect(p, state, PotionEffectType.JUMP_BOOST);
        }
        state.nextRefresh = now + 250L;
    }

    private boolean applyEffect(Player p, ArcherState state, PotionEffectType type, int level, int ticks) {
        return applyEffect(p, state, type, level, ticks, false);
    }

    private boolean applyEffect(Player p, ArcherState state, PotionEffectType type, int level, int ticks, boolean ability) {
        if (level <= 0 || ticks <= 0) {
            removeEffect(p, state, type);
            return false;
        }
        PotionEffect current = p.getPotionEffect(type);
        boolean owned = state.effects.containsKey(type);
        if (current != null) {
            if (!owned && current.getAmplifier() >= level - 1) return false;
            if (!ability && owned && current.getAmplifier() == level - 1 && current.getDuration() >= Math.max(1, ticks / 2)) return true;
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
            if (ability && type.equals(PotionEffectType.SPEED)) {
                int passiveLevel = strength("classes.archer.effects.speed", 2);
                int passiveTicks = duration("classes.archer.effects.speed", 5);
                if (passiveLevel > 0 && passiveLevel < level && passiveTicks > 0) {
                    // A longer, weaker effect becomes the boost's native fallback and resumes on its expiry tick.
                    int fallbackTicks = (int) Math.min(Integer.MAX_VALUE, (long) ticks + passiveTicks);
                    if (p.addPotionEffect(new PotionEffect(type, fallbackTicks, passiveLevel - 1, true, false, true))) {
                        state.effects.put(type, previous);
                    }
                }
            }
            if (!p.addPotionEffect(new PotionEffect(type, ticks, level - 1, true, false, true))) return false;
            state.effects.put(type, previous);
            return true;
        } finally {
            changingEffects = false;
        }
    }

    private void removeEffect(Player p, ArcherState state, PotionEffectType type) {
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
        ArcherState state = archers.remove(p.getUniqueId());
        if (state == null) return;
        for (PotionEffectType type : List.copyOf(state.effects.keySet())) removeEffect(p, state, type);
        if (notify && state.active) p.sendMessage(C.chat(localeConfig.getString("events.player.archer.deactivated")));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffectChange(EntityPotionEffectEvent e) {
        if (changingEffects || !(e.getEntity() instanceof Player p)
                || e.getCause() == EntityPotionEffectEvent.Cause.EXPIRATION
                || e.getAction() == EntityPotionEffectEvent.Action.CHANGED && !e.isOverride()) return;
        ArcherState state = archers.get(p.getUniqueId());
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
        archers.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAbility(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.useItemInHand() == Event.Result.DENY || e.getHand() == null || e.getItem() == null) return;
        Material material = e.getItem().getType();
        if (material != Material.SUGAR && material != Material.FEATHER) return;
        Player p = e.getPlayer();
        ArcherState state = update(p);
        if (state == null || !state.active) return;
        boolean speed = material == Material.SUGAR;
        Timer.Type timerType = speed ? Timer.Type.ARCHERSPEED : Timer.Type.ARCHERJUMP;
        User user = User.get(p);
        if (user.hasTimer(timerType)) {
            e.setCancelled(true);
            long seconds = (user.getTimer(timerType).getRemainingTimeMillis() + 999L) / 1000L;
            p.sendMessage(C.chat(localeConfig.getString("events.player.archer.cooldown")
                    .replace("%ability%", speed ? "Speed" : "Jump")
                    .replace("%time%", C.formatChatTime(seconds))));
            return;
        }
        String path = "classes.archer.abilities." + (speed ? "speed" : "jump");
        int ticks = duration(path, 5);
        int level = strength(path, speed ? 3 : 4);
        if (ticks <= 0 || level <= 0) return;
        ItemStack item = p.getInventory().getItem(e.getHand());
        if (item == null || item.getType() != material || item.getAmount() <= 0) return;
        if (!applyEffect(p, state, speed ? PotionEffectType.SPEED : PotionEffectType.JUMP_BOOST, level, ticks, true)) return;
        long until = System.currentTimeMillis() + ticks * 50L;
        if (speed) state.speedUntil = until;
        else state.jumpUntil = until;
        item.setAmount(item.getAmount() - 1);
        p.getInventory().setItem(e.getHand(), item.getAmount() == 0 ? null : item);
        if (timerType.getLength() > 0) user.addTimer(timerType, new Timer(timerType, true));
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBowShot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getBow() == null || e.getBow().getType() != Material.BOW
                || e.getForce() < 1.0F || !(e.getProjectile() instanceof AbstractArrow arrow)) return;
        ArcherState state = update(p);
        if (state != null && state.active) arrow.getPersistentDataContainer().set(shotKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTaggedDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        User user = UserDamageEvent.getVictimUser(victim);
        if (user == null || !user.hasTimer(Timer.Type.ARCHERTAG)) return;
        double multiplier = config.getDouble("classes.archer.tag.damage-multiplier", 1.25);
        double damage = e.getDamage() * multiplier;
        if (Double.isFinite(multiplier) && multiplier >= 1.0 && Double.isFinite(damage)) e.setDamage(damage);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArcherHit(EntityDamageByEntityEvent e) {
        if (e.getFinalDamage() <= 0 || !(e.getDamager() instanceof AbstractArrow arrow)
                || !(arrow.getShooter() instanceof Player p) || !(e.getEntity() instanceof LivingEntity victim)
                || !arrow.getPersistentDataContainer().has(shotKey, PersistentDataType.BYTE)) return;
        User user = UserDamageEvent.getVictimUser(victim);
        if (user == null || user.getUUID().equals(p.getUniqueId()) || Timer.Type.ARCHERTAG.getLength() <= 0) return;
        user.addTimer(Timer.Type.ARCHERTAG, new Timer(Timer.Type.ARCHERTAG));
        Main.getInstance().refreshNameColors();
    }
}
