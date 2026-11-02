package me.Thomas.HCF.Events.Player.Combat;

import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
import java.util.Set;
import java.util.Collections;
import java.util.IdentityHashMap;

public class UserRogueEvent implements Listener {

    private record SavedEffect(PotionEffect effect, long expires) {}

    private static class RogueState {
        private final long equippedAt = System.currentTimeMillis();
        private boolean active;
        private long speedUntil;
        private long jumpUntil;
        private long nextRefresh;
        private final Map<PotionEffectType, SavedEffect> effects = new HashMap<>();
    }

    private final Map<UUID, RogueState> rogues = new HashMap<>();
    private final FileConfiguration config = Main.getInstance().getConfig();
    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();
    private final Set<EntityDamageByEntityEvent> backstabs = Collections.newSetFromMap(new IdentityHashMap<>());
    private final BukkitTask task;
    private boolean changingEffects;

    public UserRogueEvent() {
        task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            for (Player p : Bukkit.getOnlinePlayers()) update(p);
        }, 1L, 1L);
    }

    private boolean isChainmail(ItemStack item, Material material) {
        return item != null && item.getType() == material;
    }

    private boolean hasChainmail(Player p) {
        PlayerInventory inventory = p.getInventory();
        return isChainmail(inventory.getHelmet(), Material.CHAINMAIL_HELMET)
                && isChainmail(inventory.getChestplate(), Material.CHAINMAIL_CHESTPLATE)
                && isChainmail(inventory.getLeggings(), Material.CHAINMAIL_LEGGINGS)
                && isChainmail(inventory.getBoots(), Material.CHAINMAIL_BOOTS);
    }

    private RogueState update(Player p) {
        if (!p.isOnline() || p.isDead() || !hasChainmail(p) || User.get(p).isInStaffMode() || User.get(p).isVanished()
                || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
            deactivate(p, p.isOnline() && !p.isDead());
            return null;
        }
        RogueState state = rogues.computeIfAbsent(p.getUniqueId(), ignored -> new RogueState());
        long now = System.currentTimeMillis();
        long activation = Math.clamp(config.getLong("classes.rogue.activation", 0L), 0L, Integer.MAX_VALUE);
        if (!state.active && now - state.equippedAt >= activation * 1000L) {
            state.active = true;
            p.sendMessage(C.chat(localeConfig.getString("events.player.rogue.activated")));
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

    private void refresh(Player p, RogueState state, long now) {
        String speed = state.speedUntil > now ? "classes.rogue.abilities.speed" : "classes.rogue.effects.speed";
        int speedTicks = state.speedUntil > now ? (int) ((state.speedUntil - now + 49L) / 50L) : duration(speed, 5);
        applyEffect(p, state, PotionEffectType.SPEED, strength(speed, state.speedUntil > now ? 5 : 3), speedTicks);
        applyEffect(p, state, PotionEffectType.RESISTANCE, strength("classes.rogue.effects.resistance", 1),
                duration("classes.rogue.effects.resistance", 5));
        if (state.jumpUntil > now) {
            applyEffect(p, state, PotionEffectType.JUMP_BOOST, strength("classes.rogue.abilities.jump", 4),
                    (int) ((state.jumpUntil - now + 49L) / 50L));
        } else {
            removeEffect(p, state, PotionEffectType.JUMP_BOOST);
        }
        state.nextRefresh = now + 250L;
    }

    private boolean applyEffect(Player p, RogueState state, PotionEffectType type, int level, int ticks) {
        return applyEffect(p, state, type, level, ticks, false);
    }

    private boolean applyEffect(Player p, RogueState state, PotionEffectType type, int level, int ticks, boolean ability) {
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
                int passiveLevel = strength("classes.rogue.effects.speed", 3);
                int passiveTicks = duration("classes.rogue.effects.speed", 5);
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

    private void removeEffect(Player p, RogueState state, PotionEffectType type) {
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
        RogueState state = rogues.remove(p.getUniqueId());
        if (state == null) return;
        for (PotionEffectType type : List.copyOf(state.effects.keySet())) removeEffect(p, state, type);
        if (notify && state.active) p.sendMessage(C.chat(localeConfig.getString("events.player.rogue.deactivated")));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffectChange(EntityPotionEffectEvent e) {
        if (changingEffects || !(e.getEntity() instanceof Player p)
                || e.getCause() == EntityPotionEffectEvent.Cause.EXPIRATION
                || e.getAction() == EntityPotionEffectEvent.Action.CHANGED && !e.isOverride()) return;
        RogueState state = rogues.get(p.getUniqueId());
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
        rogues.clear();
        backstabs.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAbility(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.useItemInHand() == Event.Result.DENY || e.getHand() == null || e.getItem() == null) return;
        Material material = e.getItem().getType();
        if (material != Material.SUGAR && material != Material.FEATHER) return;
        Player p = e.getPlayer();
        RogueState state = update(p);
        if (state == null || !state.active) return;
        boolean speed = material == Material.SUGAR;
        Timer.Type timerType = speed ? Timer.Type.ROGUESPEED : Timer.Type.ROGUEJUMP;
        User user = User.get(p);
        if (user.hasTimer(timerType)) {
            e.setCancelled(true);
            long seconds = (user.getTimer(timerType).getRemainingTimeMillis() + 999L) / 1000L;
            p.sendMessage(C.chat(localeConfig.getString("events.player.rogue.cooldown")
                    .replace("%ability%", speed ? "Speed" : "Jump")
                    .replace("%time%", C.formatChatTime(seconds))));
            return;
        }
        String path = "classes.rogue.abilities." + (speed ? "speed" : "jump");
        int ticks = duration(path, 5);
        int level = strength(path, speed ? 5 : 4);
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

    private boolean behind(Player attacker, Player victim) {
        Location source = attacker.getLocation(), target = victim.getLocation();
        if (!source.getWorld().equals(target.getWorld())) return false;
        double x = source.getX() - target.getX(), z = source.getZ() - target.getZ();
        double length = Math.hypot(x, z);
        if (length < 0.001) return false;
        double yaw = Math.toRadians(target.getYaw());
        double dot = (-Math.sin(yaw) * x + Math.cos(yaw) * z) / length;
        double angle = config.getDouble("classes.rogue.backstab.angle", 45.0);
        if (!Double.isFinite(angle)) angle = 45.0;
        return dot <= -Math.cos(Math.toRadians(Math.clamp(angle, 0.0, 90.0)));
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBackstab(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !(e.getEntity() instanceof Player victim)
                || p.getUniqueId().equals(victim.getUniqueId()) || victim.isDead()
                || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || p.getInventory().getItemInMainHand().getType() != Material.GOLDEN_SWORD
                || e.getDamage() <= 0 || !behind(p, victim)) return;
        RogueState state = update(p);
        if (state == null || !state.active) return;
        User user = User.get(p);
        if (user.hasTimer(Timer.Type.ROGUEBACKSTAB)) {
            long seconds = (user.getTimer(Timer.Type.ROGUEBACKSTAB).getRemainingTimeMillis() + 999L) / 1000L;
            p.sendMessage(C.chat(localeConfig.getString("events.player.rogue.backstab-cooldown")
                    .replace("%time%", C.formatChatTime(seconds))));
            return;
        }
        double hearts = config.getDouble("classes.rogue.backstab.damage-hearts", 4.0);
        double damage = e.getDamage(EntityDamageEvent.DamageModifier.BASE) + hearts * 2.0;
        if (!Double.isFinite(hearts) || hearts <= 0 || !Double.isFinite(damage)) return;
        // Changing this modifier directly leaves all existing reductions unchanged: the bonus reaches health intact.
        e.setDamage(EntityDamageEvent.DamageModifier.BASE, damage);
        backstabs.add(e);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBackstabAccepted(EntityDamageByEntityEvent e) {
        if (!backstabs.remove(e) || e.isCancelled() || e.getFinalDamage() <= 0) return;
        Player p = (Player) e.getDamager();
        ItemStack sword = p.getInventory().getItemInMainHand();
        if (sword.getType() != Material.GOLDEN_SWORD || sword.getAmount() <= 0) return;
        sword.setAmount(sword.getAmount() - 1);
        p.getInventory().setItemInMainHand(sword.getAmount() == 0 ? null : sword);
        if (Timer.Type.ROGUEBACKSTAB.getLength() > 0) {
            User.get(p).addTimer(Timer.Type.ROGUEBACKSTAB, new Timer(Timer.Type.ROGUEBACKSTAB, true));
        }
    }
}
