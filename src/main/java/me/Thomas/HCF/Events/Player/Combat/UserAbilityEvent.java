package me.Thomas.HCF.Events.Player.Combat;

import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Ability.AbilityItems;
import me.Thomas.HCF.Utils.Ability.AbilityItems.Type;
import me.Thomas.HCF.Utils.Items.Items;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class UserAbilityEvent implements Listener {

    private record Streak(UUID attacker, Type type, int hits, long time) { }
    private final Map<UUID, Streak> streaks = new HashMap<>();
    private final Map<UUID, UUID> lastAttackers = new HashMap<>();
    private final Map<UUID, Long> boned = new HashMap<>();
    private final Map<UUID, BukkitTask> ninjas = new HashMap<>();

    private boolean available(Player p) {
        User user = User.get(p);
        Faction territory = Faction.getAtLocation(p.getLocation());
        return p.isOnline() && !p.isDead() && !user.isInStaffMode() && !user.isVanished()
                && (Main.getInstance().getStaffMode() == null || !Main.getInstance().getStaffMode().isFrozen(p))
                && !user.hasPvpProtection() && !(territory != null && territory.getType() == Faction.Type.SAFEZONE)
                && (!Main.getInstance().isSOTWOngoing() || Main.getInstance().isSOTWEnabled(p.getUniqueId()));
    }

    private boolean combat(Player p, Player victim) {
        return !p.getUniqueId().equals(victim.getUniqueId()) && available(p) && available(victim)
                && UserDamageEvent.canCombat(victim, p);
    }

    private int integer(Type type, String option, int fallback) {
        return Math.clamp(Main.getInstance().getConfig().getInt(type.path() + "." + option, fallback), 0, 86400);
    }

    private boolean consume(Player p, EquipmentSlot hand, Type type) {
        ItemStack item = p.getInventory().getItem(hand);
        if (AbilityItems.getType(item) != type) return false;
        item.setAmount(item.getAmount() - 1);
        p.getInventory().setItem(hand, item.getAmount() == 0 ? null : item);
        return true;
    }

    private void effects(Player p, Type type) {
        for (Map<?, ?> effect : Main.getInstance().getConfig().getMapList(type.path() + ".effects")) {
            Object configured = effect.get("type");
            if (!(configured instanceof String name)) continue;
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
            PotionEffectType effectType = key == null ? null : Registry.EFFECT.get(key);
            if (effectType == null) continue;
            int level = effect.get("level") instanceof Number number ? Math.clamp(number.intValue(), 1, 256) : 1;
            int duration = effect.get("duration") instanceof Number number ? Math.clamp(number.intValue(), 1, 86400) : 5;
            p.addPotionEffect(new PotionEffect(effectType, duration * 20, level - 1));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        Type type = AbilityItems.getType(e.getItem());
        if (type == null || type == Type.ARCHER || !e.getAction().isRightClick() || e.getHand() == null) return;
        if (e.useItemInHand() == Event.Result.DENY) return;
        e.setCancelled(true);
        if (type != Type.SWITCHER && type != Type.RAGE && type != Type.BRICK && type != Type.NINJA) return;
        Player p = e.getPlayer();
        if (!available(p)) { AbilityItems.message(p, "unavailable"); return; }
        if (!AbilityItems.ready(p, type, true)) return;
        if (type == Type.SWITCHER || type == Type.RAGE) {
            Class<? extends Projectile> projectileType = type == Type.SWITCHER ? Snowball.class : Egg.class;
            Projectile projectile = p.launchProjectile(projectileType, p.getEyeLocation().getDirection().multiply(1.5), shot ->
                    shot.getPersistentDataContainer().set(AbilityItems.key("type"), PersistentDataType.STRING, type.getId()));
            // The launch monitor only marks projectiles which survived other plugins' launch checks.
            if (!projectile.getPersistentDataContainer().has(AbilityItems.key("launched"))) { projectile.remove(); return; }
            if (!consume(p, e.getHand(), type)) { projectile.remove(); return; }
            AbilityItems.cooldown(p, type);
        } else if (type == Type.BRICK) {
            if (!consume(p, e.getHand(), type)) return;
            effects(p, type);
            AbilityItems.cooldown(p, type);
        } else {
            if (ninjas.containsKey(p.getUniqueId())) return;
            UUID attacker = lastAttackers.get(p.getUniqueId());
            Player target = attacker == null ? null : Bukkit.getPlayer(attacker);
            if (target == null || !target.getWorld().equals(p.getWorld()) || !combat(p, target)) {
                AbilityItems.message(p, "no-attacker");
                return;
            }
            if (!consume(p, e.getHand(), type)) return;
            AbilityItems.cooldown(p, type);
            AbilityItems.message(target, "ninja-warning", "%player%", p.getName(), "%time%", Integer.toString(integer(type, "delay", 3)));
            AbilityItems.message(p, "ninja-start", "%player%", target.getName());
            ninjas.put(p.getUniqueId(), Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
                ninjas.remove(p.getUniqueId());
                Player current = Bukkit.getPlayer(attacker);
                if (current == null || !current.getWorld().equals(p.getWorld()) || !combat(p, current)
                        || !p.teleport(current.getLocation(), TeleportCause.PLUGIN)) AbilityItems.message(p, "teleport-failed");
                else AbilityItems.message(p, "ninja-success", "%player%", current.getName());
            }, integer(type, "delay", 3) * 20L));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(AbilityItems.key("type"))) {
            e.getEntity().getPersistentDataContainer().set(AbilityItems.key("launched"), PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent e) {
        Type type = AbilityItems.getType(e.getEntity().getPersistentDataContainer().get(AbilityItems.key("type"), PersistentDataType.STRING));
        if (type != Type.RAGE) return;
        e.setCancelled(true);
        Projectile shot = e.getEntity();
        shot.remove();
        if (e.getHitBlock() == null || !(shot.getShooter() instanceof Player p) || !available(p)) return;
        double radius = integer(type, "radius", 3);
        if (p.getWorld().equals(shot.getWorld()) && p.getLocation().distanceSquared(shot.getLocation()) <= radius * radius) effects(p, type);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBowShot(EntityShootBowEvent e) {
        ItemStack bow = e.getBow();
        if (!(e.getEntity() instanceof Player p) || !(e.getProjectile() instanceof AbstractArrow arrow)
                || bow == null || AbilityItems.getType(bow) != Type.ARCHER) return;
        ItemMeta meta = bow.getItemMeta();
        if (meta == null) return;
        if (!available(p) || !AbilityItems.ready(p, Type.ARCHER, true) || e.getForce() < 1.0F) return;
        String identity = meta.getPersistentDataContainer().get(AbilityItems.key("identity"), PersistentDataType.STRING);
        if (identity != null) arrow.getPersistentDataContainer().set(AbilityItems.key("bow"), PersistentDataType.STRING, identity);
    }

    private boolean chargeBow(Player p, String identity) {
        for (int slot = 0; slot < p.getInventory().getSize(); slot++) {
            ItemStack bow = p.getInventory().getItem(slot);
            if (bow == null || AbilityItems.getType(bow) != Type.ARCHER) continue;
            ItemMeta meta = bow.getItemMeta();
            if (meta == null) continue;
            if (!identity.equals(meta.getPersistentDataContainer().get(AbilityItems.key("identity"), PersistentDataType.STRING))) continue;
            int uses = meta.getPersistentDataContainer().getOrDefault(AbilityItems.key("uses"), PersistentDataType.INTEGER, 0);
            if (uses <= 0) return false;
            if (uses == 1) p.getInventory().setItem(slot, null);
            else {
                meta.getPersistentDataContainer().set(AbilityItems.key("uses"), PersistentDataType.INTEGER, uses - 1);
                Items.decorate(meta, "abilities.archer", Map.of("%uses%", Integer.toString(uses - 1)));
                bow.setItemMeta(meta);
                p.getInventory().setItem(slot, bow);
            }
            return true;
        }
        return false;
    }

    private Player attacker(EntityDamageEvent e) {
        Player p = UserDamageEvent.resolveAttacker(e.getDamageSource().getCausingEntity());
        if (p == null && e instanceof EntityDamageByEntityEvent hit) p = UserDamageEvent.resolveAttacker(hit.getDamager());
        return p;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player p = attacker(e);
        if (p == null) {
            if (e.getFinalDamage() > 0 && e instanceof EntityDamageByEntityEvent) streaks.remove(victim.getUniqueId());
            return;
        }
        if (p.getUniqueId().equals(victim.getUniqueId())) {
            if (e.getFinalDamage() > 0) streaks.remove(victim.getUniqueId());
            return;
        }
        boolean switcher = e instanceof EntityDamageByEntityEvent hit && hit.getDamager() instanceof Snowball shot
                && Type.SWITCHER == AbilityItems.getType(shot.getPersistentDataContainer().get(AbilityItems.key("type"), PersistentDataType.STRING));
        if (e.getFinalDamage() <= 0 && !(e instanceof EntityDamageByEntityEvent)) return;
        if (!combat(p, victim)) return;
        Streak previous = streaks.remove(victim.getUniqueId());
        if (e.getFinalDamage() <= 0 && !switcher) return;
        if (e.getFinalDamage() > 0) lastAttackers.put(victim.getUniqueId(), p.getUniqueId());
        if (!(e instanceof EntityDamageByEntityEvent hit)) return;
        if (switcher) {
            Projectile shot = (Projectile) hit.getDamager();
            shot.getPersistentDataContainer().remove(AbilityItems.key("type"));
            double distance = integer(Type.SWITCHER, "max-distance", 10);
            if (!p.getWorld().equals(victim.getWorld()) || p.getLocation().distanceSquared(victim.getLocation()) > distance * distance) {
                AbilityItems.message(p, "too-far");
                return;
            }
            Location source = p.getLocation(), destination = victim.getLocation();
            if (p.teleport(destination, TeleportCause.PLUGIN)) {
                if (!victim.teleport(source, TeleportCause.PLUGIN)) p.teleport(source, TeleportCause.PLUGIN);
                else {
                    combatTimers(p, victim);
                    AbilityItems.message(p, "swapped", "%player%", victim.getName());
                }
            }
            return;
        }
        if (hit.getDamager() instanceof AbstractArrow arrow) {
            String identity = arrow.getPersistentDataContainer().get(AbilityItems.key("bow"), PersistentDataType.STRING);
            if (identity == null || !AbilityItems.ready(p, Type.ARCHER, true) || Timer.Type.ARCHERTAG.getLength() <= 0) return;
            if (!chargeBow(p, identity)) return;
            arrow.getPersistentDataContainer().remove(AbilityItems.key("bow"));
            User.get(victim).addTimer(Timer.Type.ARCHERTAG, new Timer(Timer.Type.ARCHERTAG));
            Main.getInstance().refreshNameColors();
            AbilityItems.cooldown(p, Type.ARCHER);
            return;
        }
        if (!(hit.getDamager() instanceof Player) || hit.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        Type type = AbilityItems.getType(p.getInventory().getItemInMainHand());
        if (type != Type.SWITCHSTICK && type != Type.BONE && type != Type.POTIONCOUNTER && type != Type.STARVATION) return;
        if (!AbilityItems.ready(p, type, true)) return;
        if (type == Type.SWITCHSTICK) {
            double chance = Main.getInstance().getConfig().getDouble(type.path() + ".chance", 0.5);
            if (!Double.isFinite(chance)) chance = 0.5;
            if (ThreadLocalRandom.current().nextDouble() >= Math.clamp(chance, 0.0, 1.0)) return;
            Location location = victim.getLocation();
            location.setYaw(location.getYaw() + 180F);
            location.setPitch(-location.getPitch());
            if (victim.teleport(location, TeleportCause.PLUGIN)) AbilityItems.cooldown(p, type);
            return;
        }
        if (type != Type.BONE && type != Type.POTIONCOUNTER && type != Type.STARVATION) return;
        long now = System.currentTimeMillis();
        int count = previous != null && previous.attacker().equals(p.getUniqueId()) && previous.type() == type
                && now - previous.time() <= integer(type, "hit-window", 10) * 1000L ? previous.hits() + 1 : 1;
        if (count < Math.max(1, integer(type, "hits", 3))) {
            streaks.put(victim.getUniqueId(), new Streak(p.getUniqueId(), type, count, now));
            AbilityItems.message(p, "hits", "%hits%", Integer.toString(count), "%required%", Integer.toString(Math.max(1, integer(type, "hits", 3))), "%player%", victim.getName());
            return;
        }
        if (!consume(p, EquipmentSlot.HAND, type)) return;
        AbilityItems.cooldown(p, type);
        if (type == Type.BONE) {
            boned.put(victim.getUniqueId(), now + integer(type, "duration", 10) * 1000L);
            AbilityItems.message(victim, "boned", "%time%", Integer.toString(integer(type, "duration", 10)));
        } else if (type == Type.STARVATION) {
            victim.setSaturation(0);
            victim.setFoodLevel(0);
            AbilityItems.message(victim, "starved");
        } else countPotions(p, victim);
        AbilityItems.message(p, "success", "%item%", type.getName(), "%player%", victim.getName());
    }

    private void countPotions(Player p, Player victim) {
        Map<String, Integer> counts = new TreeMap<>();
        for (ItemStack item : victim.getInventory().getContents()) {
            if (item == null || (item.getType() != Material.POTION && item.getType() != Material.SPLASH_POTION
                    && item.getType() != Material.LINGERING_POTION) || !(item.getItemMeta() instanceof PotionMeta meta)) continue;
            String name = item.getType().name().replace('_', ' ').toLowerCase(Locale.ROOT);
            if (meta.getBasePotionType() != null) name += " - " + meta.getBasePotionType().name().replace('_', ' ').toLowerCase(Locale.ROOT);
            for (PotionEffect effect : meta.getCustomEffects()) name += " / " + effect.getType().getKey().getKey()
                    + " " + (effect.getAmplifier() + 1) + " (" + effect.getDuration() / 20 + "s)";
            counts.merge(name, item.getAmount(), Integer::sum);
        }
        AbilityItems.message(p, "potions-header", "%player%", victim.getName());
        if (counts.isEmpty()) AbilityItems.message(p, "no-potions");
        counts.forEach((name, count) -> AbilityItems.message(p, "potion-line", "%potion%", name, "%amount%", Integer.toString(count)));
    }

    private void combatTimers(Player p, Player victim) {
        UserDamageEvent.applySuccessfulCombat(victim, p);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getFinalDamage() <= 0 || e.getFinalDamage() < p.getHealth()
                || !available(p) || !AbilityItems.ready(p, Type.SECONDCHANCE, false)) return;
        Player attacker = attacker(e);
        if (attacker != null && !attacker.getUniqueId().equals(p.getUniqueId()) && !combat(attacker, p)) return;
        for (int slot = 0; slot < p.getInventory().getSize(); slot++) {
            ItemStack item = p.getInventory().getItem(slot);
            if (AbilityItems.getType(item) != Type.SECONDCHANCE) continue;
            item.setAmount(item.getAmount() - 1);
            p.getInventory().setItem(slot, item.getAmount() == 0 ? null : item);
            e.setCancelled(true);
            p.setHealth(Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue());
            p.setNoDamageTicks(10);
            if (attacker != null && !attacker.getUniqueId().equals(p.getUniqueId())) {
                lastAttackers.put(p.getUniqueId(), attacker.getUniqueId());
                streaks.remove(p.getUniqueId());
                combatTimers(attacker, p);
            }
            AbilityItems.cooldown(p, Type.SECONDCHANCE);
            AbilityItems.message(p, "second-chance");
            return;
        }
    }

    private boolean restricted(Player p, String action) {
        Long expires = boned.get(p.getUniqueId());
        if (expires == null) return false;
        if (expires <= System.currentTimeMillis()) { boned.remove(p.getUniqueId()); return false; }
        if (!Main.getInstance().getConfig().getBoolean(Type.BONE.path() + ".prevent-" + action, true)) return false;
        AbilityItems.message(p, "bone-restricted");
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { if (restricted(e.getPlayer(), "breaking")) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { if (restricted(e.getPlayer(), "building")) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(org.bukkit.event.player.PlayerItemConsumeEvent e) {
        if (AbilityItems.getType(e.getItem()) != null) e.setCancelled(true);
    }

    private void cleanup(UUID uuid) {
        streaks.remove(uuid);
        streaks.values().removeIf(streak -> streak.attacker().equals(uuid));
        lastAttackers.remove(uuid);
        lastAttackers.values().removeIf(uuid::equals);
        boned.remove(uuid);
        BukkitTask task = ninjas.remove(uuid);
        if (task != null) task.cancel();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) { cleanup(e.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) { cleanup(e.getEntity().getUniqueId()); }

    @EventHandler
    public void onDisable(PluginDisableEvent e) {
        if (e.getPlugin() != Main.getInstance()) return;
        ninjas.values().forEach(BukkitTask::cancel);
        ninjas.clear();
        streaks.clear();
        lastAttackers.clear();
        boned.clear();
    }
}
