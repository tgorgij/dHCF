package me.Thomas.HCF.Events.Player.Combat;

import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class UserBardEvent implements Listener {

    private record Effect(PotionEffect potion, double energy, boolean enemies) {}

    private record SavedEffect(PotionEffect effect, long expires) {}

    private static class BardState {
        private final long equippedAt = System.currentTimeMillis();
        private long updatedAt = equippedAt;
        private long nextHeld;
        private final Map<PotionEffectType, SavedEffect> nativeEffects = new HashMap<>();
    }

    private final Map<UUID, BardState> bards = new HashMap<>();
    private final Map<Material, Effect> heldEffects = new HashMap<>();
    private final Map<Material, Effect> clickEffects = new HashMap<>();
    private final BukkitTask task;
    private boolean changingNativeEffects;

    public UserBardEvent() {
        ConfigurationSection items = Main.getInstance().getConfig().getConfigurationSection("classes.bard.items");
        if (items != null) {
            for (String name : items.getKeys(false)) {
                Material material = Material.matchMaterial(name);
                if (material == null || material.isAir() || !material.isItem()) {
                    Main.getInstance().getLogger().warning("Invalid Bard item: " + name);
                    continue;
                }
                loadEffect(material, items.getConfigurationSection(name + ".held"), false);
                loadEffect(material, items.getConfigurationSection(name + ".click"), true);
            }
        }
        task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            for (Player p : Bukkit.getOnlinePlayers()) update(p, true);
        }, 1L, 5L);
    }

    private void loadEffect(Material material, ConfigurationSection section, boolean click) {
        if (section == null) return;
        String name = section.getString("effect", "").toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(name);
        PotionEffectType type = key == null ? null : Registry.EFFECT.get(key);
        int level = section.getInt("strength", 1);
        long seconds = section.getLong("duration", 5L);
        double energy = click ? section.getDouble("energy", 20.0) : 0;
        String target = section.getString("target", "FACTION").toUpperCase(Locale.ROOT);
        if (type == null || level < 1 || level > 256 || seconds < 1 || seconds > Integer.MAX_VALUE / 20L
                || !Double.isFinite(energy) || energy < 0 || (!target.equals("FACTION") && !target.equals("ENEMIES"))
                || !click && target.equals("ENEMIES")) {
            Main.getInstance().getLogger().warning("Invalid Bard " + (click ? "click" : "held") + " effect for " + material);
            return;
        }
        Effect effect = new Effect(new PotionEffect(type, (int) (seconds * 20L), level - 1, true, false, true),
                energy, target.equals("ENEMIES"));
        (click ? clickEffects : heldEffects).put(material, effect);
    }

    private boolean gold(ItemStack item, Material type) { return item != null && item.getType() == type; }

    private boolean equipped(Player p) {
        PlayerInventory inventory = p.getInventory();
        return gold(inventory.getHelmet(), Material.GOLDEN_HELMET)
                && gold(inventory.getChestplate(), Material.GOLDEN_CHESTPLATE)
                && gold(inventory.getLeggings(), Material.GOLDEN_LEGGINGS)
                && gold(inventory.getBoots(), Material.GOLDEN_BOOTS);
    }

    private boolean available(Player p) {
        User user = User.get(p);
        return p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR
                && !user.isInStaffMode() && !user.isVanished();
    }

    private double setting(String key, double fallback) {
        double value = Main.getInstance().getConfig().getDouble("classes.bard." + key, fallback);
        return Double.isFinite(value) && value >= 0 ? value : fallback;
    }

    private BardState update(Player p, boolean held) {
        if (!available(p) || !equipped(p)) {
            deactivate(p, true);
            return null;
        }
        User user = User.get(p);
        BardState state = bards.get(p.getUniqueId());
        if (state == null) {
            state = new BardState();
            bards.put(p.getUniqueId(), state);
            user.setBardEnergy(0);
        }
        long now = System.currentTimeMillis();
        long activation = Math.clamp(Main.getInstance().getConfig().getLong("classes.bard.activation", 0L), 0L, Integer.MAX_VALUE);
        if (!user.isBardActive()) {
            state.updatedAt = now;
            if (now - state.equippedAt < activation * 1000L) return state;
            user.setBardActive(true);
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.bard.activated")));
        }
        refreshNative(p, state, PotionEffectType.SPEED, "speed", 2);
        refreshNative(p, state, PotionEffectType.RESISTANCE, "resistance", 1);
        refreshNative(p, state, PotionEffectType.FIRE_RESISTANCE, "fire_resistance", 1);
        double regeneration = setting("energy.regeneration", 2.0);
        user.setBardEnergy(Math.min(setting("energy.maximum", 100.0),
                user.getBardEnergy() + Math.max(0L, now - state.updatedAt) / 1000.0 * regeneration));
        state.updatedAt = now;
        if (held && now >= state.nextHeld) {
            state.nextHeld = now + 1000L;
            ItemStack item = p.getInventory().getItemInMainHand();
            Effect effect = item == null ? null : heldEffects.get(item.getType());
            if (effect != null) apply(p, effect);
        }
        return state;
    }

    private void refreshNative(Player p, BardState state, PotionEffectType type, String name, int fallback) {
        String path = "classes.bard.effects." + name;
        int strength = Math.clamp(Main.getInstance().getConfig().getInt(path + ".strength", fallback), 0, 256);
        int ticks = (int) Math.clamp(Main.getInstance().getConfig().getLong(path + ".duration", 5), 0L, Integer.MAX_VALUE / 20L) * 20;
        if (strength == 0 || ticks == 0) return;
        PotionEffect current = p.getPotionEffect(type);
        boolean owned = state.nativeEffects.containsKey(type);
        if (current != null && current.getAmplifier() == strength - 1
                && (!owned || current.getDuration() >= Math.max(1, ticks / 2))) return;
        SavedEffect previous = state.nativeEffects.get(type);
        if (!owned) previous = new SavedEffect(current, current == null ? 0L : current.isInfinite() ? Long.MAX_VALUE
                : System.currentTimeMillis() + current.getDuration() * 50L);
        changingNativeEffects = true;
        try {
            boolean applied = p.addPotionEffect(new PotionEffect(type, ticks, strength - 1, true, false, true));
            // A weaker native effect can sit behind a stronger boost without replacing it.
            if (applied && (current == null || current.getAmplifier() <= strength - 1)) state.nativeEffects.put(type, previous);
        } finally {
            changingNativeEffects = false;
        }
    }

    private void removeNative(Player p, BardState state) {
        changingNativeEffects = true;
        try {
            for (Map.Entry<PotionEffectType, SavedEffect> entry : state.nativeEffects.entrySet()) {
                p.removePotionEffect(entry.getKey());
                SavedEffect previous = entry.getValue();
                if (previous.effect() == null) continue;
                long remaining = previous.expires() - System.currentTimeMillis();
                if (previous.effect().isInfinite()) p.addPotionEffect(previous.effect());
                else if (remaining > 0) p.addPotionEffect(previous.effect().withDuration((int) ((remaining + 49L) / 50L)));
            }
        } finally {
            changingNativeEffects = false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffectChange(EntityPotionEffectEvent e) {
        if (changingNativeEffects || !(e.getEntity() instanceof Player p)
                || e.getCause() == EntityPotionEffectEvent.Cause.EXPIRATION
                || e.getAction() == EntityPotionEffectEvent.Action.CHANGED && !e.isOverride()) return;
        BardState state = bards.get(p.getUniqueId());
        if (state != null) state.nativeEffects.remove(e.getModifiedType());
    }

    private boolean sameFaction(Player bard, Player target) {
        User user = User.get(bard), recipient = User.get(target);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        return faction != null && faction.getType() == Faction.Type.PLAYER && faction.hasMember(user.getUUID())
                && faction.getUUID().equals(recipient.getFaction()) && faction.hasMember(recipient.getUUID());
    }

    private boolean apply(Player bard, Effect effect) {
        List<Player> targets = new ArrayList<>();
        targets.add(bard);
        double radius = Math.min(128.0, setting("radius", 15.0));
        Location origin = bard.getLocation();
        for (Entity entity : bard.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player target) || target.getUniqueId().equals(bard.getUniqueId()) || !available(target)) continue;
            Location location = target.getLocation();
            if (Math.abs(location.getX() - origin.getX()) > radius || Math.abs(location.getY() - origin.getY()) > radius
                    || Math.abs(location.getZ() - origin.getZ()) > radius) continue;
            boolean team = sameFaction(bard, target);
            if (effect.enemies() ? !team : team) targets.add(target);
        }
        boolean applied = false;
        boolean harmful = effect.potion().getType().getEffectCategory() == PotionEffectType.Category.HARMFUL;
        for (Player target : targets) {
            boolean self = target.getUniqueId().equals(bard.getUniqueId());
            if (!self && (effect.enemies() || harmful) && !UserDamageEvent.canCombat(target, bard)) continue;
            // Native potion merging preserves stronger boosts and resumes weaker held effects when they expire.
            if (!target.addPotionEffect(effect.potion())) continue;
            applied = true;
            if (!self && harmful) UserDamageEvent.applySuccessfulPotionCombat(target, bard);
        }
        return applied;
    }

    private void deactivate(Player p, boolean notify) {
        BardState state = bards.remove(p.getUniqueId());
        User user = User.get(p);
        if (state != null) removeNative(p, state);
        if (state != null && user.isBardActive() && notify && p.isOnline() && !p.isDead()) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.bard.deactivated")));
        }
        user.setBardActive(false);
    }

    @EventHandler
    public void onEquipmentChange(EntityEquipmentChangedEvent e) {
        if (e.getEntity() instanceof Player p) update(p, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAbility(PlayerInteractEvent e) {
        if ((e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
                || e.getHand() != EquipmentSlot.HAND || e.useItemInHand() == Event.Result.DENY || e.getItem() == null) return;
        Effect effect = clickEffects.get(e.getItem().getType());
        if (effect == null || update(e.getPlayer(), false) == null || !User.get(e.getPlayer()).isBardActive()) return;
        e.setUseItemInHand(Event.Result.DENY);
        e.setUseInteractedBlock(Event.Result.DENY);
        Player p = e.getPlayer();
        User user = User.get(p);
        if (user.hasTimer(Timer.Type.BARD)) {
            long seconds = (user.getTimer(Timer.Type.BARD).getRemainingTimeMillis() + 999L) / 1000L;
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.bard.cooldown")
                    .replace("%time%", C.formatChatTime(seconds))));
            return;
        }
        if (user.getBardEnergy() < effect.energy()) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.bard.energy")
                    .replace("%energy%", Double.toString(effect.energy()))));
            return;
        }
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item == null || item.getType() != e.getItem().getType() || item.getAmount() <= 0 || !apply(p, effect)) return;
        user.setBardEnergy(Math.max(0, user.getBardEnergy() - effect.energy()));
        item.setAmount(item.getAmount() - 1);
        p.getInventory().setItemInMainHand(item.getAmount() == 0 ? null : item);
        if (Timer.Type.BARD.getLength() > 0) user.addTimer(Timer.Type.BARD, new Timer(Timer.Type.BARD, true));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        deactivate(e.getPlayer(), false);
        User.get(e.getPlayer()).setBardEnergy(0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        deactivate(e.getEntity(), false);
        User.get(e.getEntity()).setBardEnergy(0);
    }

    @EventHandler
    public void onDisable(PluginDisableEvent e) {
        if (e.getPlugin() != Main.getInstance()) return;
        task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) {
            deactivate(p, false);
            User.get(p).setBardEnergy(0);
        }
        bards.clear();
    }
}
