package me.Thomas.HCF.Events.Player.Combat;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.ArrayList;

import java.util.Objects;
import java.util.UUID;

public class UserDamageEvent implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_ENTITY
                || !(e.getCaught() instanceof LivingEntity victim) || getVictimUser(victim) == null) return;
        if (e.getPlayer().getUniqueId().equals(getVictimUser(victim).getUUID()) || !canCombat(victim, e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSuccessfulFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_ENTITY
                && e.getCaught() instanceof LivingEntity victim && getVictimUser(victim) != null) {
            applySuccessfulCombat(victim, e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void UserDamageUser(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim) || getVictimUser(victim) == null) return;
        if (!(e.getDamager() instanceof Player attacker)) return;

        handleCombat(e, victim, attacker);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void UserDamageUserProjectile(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim) || getVictimUser(victim) == null) return;
        if (!(e.getDamager() instanceof Projectile projectile)) return;
        if (!(projectile.getShooter() instanceof Player attacker)) return;

        handleCombat(e, victim, attacker);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void UserDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim) || getVictimUser(victim) == null) return;

        if (e.getCause() == EntityDamageEvent.DamageCause.FIRE
                || e.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK) {
            if (isDamageProtected(victim)) e.setCancelled(true);
            return;
        }

        Player attacker = resolveAttacker(e.getDamageSource().getCausingEntity());
        if (attacker == null && e instanceof EntityDamageByEntityEvent entityDamage) {
            attacker = resolveAttacker(entityDamage.getDamager());
        }
        if (attacker != null && !attacker.getUniqueId().equals(getVictimUser(victim).getUUID())) {
            // Direct player and projectile hits have their own handlers.
            if (e instanceof EntityDamageByEntityEvent entityDamage
                    && (entityDamage.getDamager() instanceof Player
                    || entityDamage.getDamager() instanceof Projectile projectile
                    && projectile.getShooter() instanceof Player)) return;
            handleCombat(e, victim, attacker);
            return;
        }
        if (isDamageProtected(victim)) e.setCancelled(true);
    }

    static User getVictimUser(LivingEntity victim) {
        if (victim instanceof Player player) return User.get(player);
        if (!(victim instanceof Villager villager)) return null;
        String playerId = villager.getPersistentDataContainer().get(
                new NamespacedKey(Main.getInstance(), "player"), PersistentDataType.STRING);
        if (playerId == null) return null;
        try {
            return User.get(UUID.fromString(playerId));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    static boolean isDamageProtected(LivingEntity victim) {
        User victimUser = Objects.requireNonNull(getVictimUser(victim));
        Faction victimTerritory = Faction.getAtLocation(victim.getLocation());
        if (victimTerritory != null && victimTerritory.getType() == Faction.Type.SAFEZONE) {
            return true;
        }
        if (victimUser.hasPvpProtection()) {
            return true;
        }
        return Main.getInstance().isSOTWOngoing() && !Main.getInstance().isSOTWEnabled(victimUser.getUUID());
    }

    private void handleCombat(EntityDamageEvent e, LivingEntity victim, Player attacker) {
        if (!canCombat(victim, attacker)) {
            e.setCancelled(true);
        }
    }

    // Apply hit side effects only after every damage protection handler has run.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSuccessfulPlayerHit(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim) || getVictimUser(victim) == null) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.FIRE
                || e.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK) return;
        Player attacker = resolveAttacker(e.getDamageSource().getCausingEntity());
        if (attacker == null && e instanceof EntityDamageByEntityEvent hit) {
            attacker = resolveAttacker(hit.getDamager());
        }
        if (attacker == null || e.getFinalDamage() <= 0) return;
        boolean self = attacker.getUniqueId().equals(getVictimUser(victim).getUUID());
        if (self && !(e instanceof EntityDamageByEntityEvent hit && hit.getDamager() instanceof AbstractArrow)) return;
        if (self) {
            applyCombatTimer(attacker, User.get(attacker));
        } else {
            applyVictimCombatTimer(victim);
            applyCombatTimer(attacker, User.get(attacker));
        }
    }

    static boolean canCombat(LivingEntity victim, Player attacker) {
        LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();
        User victimUser = Objects.requireNonNull(getVictimUser(victim));
        User attackerUser = User.get(attacker.getUniqueId());

        if (attackerUser.hasFaction() && attackerUser.getFaction().equals(victimUser.getFaction())
                && !Main.getInstance().isFriendlyFireEnabled(attackerUser.getFaction())) {
            attacker.sendMessage(C.chat(localeConfig.getString("events.player.combat.faction")
                    .replace("%victim%", victimUser.getName())));
            return false;
        }

        Faction attackerTerritory = Faction.getAtLocation(attacker.getLocation());
        if (attackerTerritory != null && attackerTerritory.getType() == Faction.Type.SAFEZONE) {
            attacker.sendMessage(C.chat(localeConfig.getString("events.player.combat.attacker-safezone")
                    .replace("%victim%", victimUser.getName())));
            return false;
        }

        Faction victimTerritory = Faction.getAtLocation(victim.getLocation());
        if (victimTerritory != null && victimTerritory.getType() == Faction.Type.SAFEZONE) {
            attacker.sendMessage(C.chat(localeConfig.getString("events.player.combat.safezone")
                    .replace("%victim%", victimUser.getName())));
            return false;
        }

        if (victimUser.hasPvpProtection()) {
            attacker.sendMessage(C.chat(localeConfig.getString("events.player.combat.pvp-timer")
                    .replace("%victim%", victimUser.getName())));
            return false;
        }

        if (attackerUser.hasPvpProtection()) {
            attacker.sendMessage(C.chat(localeConfig.getString("events.player.combat.attacker-pvp-timer")
                    .replace("%victim%", victimUser.getName())));
            return false;
        }

        if (Main.getInstance().isSOTWOngoing()) {
            if (!Main.getInstance().isSOTWEnabled(victimUser.getUUID())) {
                attacker.sendMessage(C.chat(localeConfig.getString("events.player.combat.sotw")
                        .replace("%victim%", victimUser.getName())));
                return false;
            }

            if (!Main.getInstance().isSOTWEnabled(attacker.getUniqueId())) {
                attacker.sendMessage(C.chat(localeConfig.getString("events.player.combat.attacker-sotw")
                        .replace("%victim%", victimUser.getName())));
                return false;
            }
        }

        return true;
    }

    static Player resolveAttacker(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        if (entity instanceof TNTPrimed tnt) return resolveAttacker(tnt.getSource());
        if (entity instanceof Tameable tameable && tameable.getOwner() instanceof Player player) return player;
        if (entity instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) return player;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void UserSplash(PotionSplashEvent e) {
        if (!hasHarmfulEffects(e.getPotion().getEffects())) return;
        Player attacker = resolveAttacker(e.getPotion());
        for (LivingEntity entity : e.getAffectedEntities()) {
            if (getVictimUser(entity) == null || e.getIntensity(entity) <= 0) continue;
            if (!applyPotionCombat(entity, attacker)) e.setIntensity(entity, 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void UserLingeringSplash(AreaEffectCloudApplyEvent e) {
        AreaEffectCloud cloud = e.getEntity();
        Collection<PotionEffect> effects = new ArrayList<>(cloud.getCustomEffects());
        if (cloud.getBasePotionType() != null) effects.addAll(cloud.getBasePotionType().getPotionEffects());
        if (!hasHarmfulEffects(effects)) return;
        Player attacker = resolveAttacker(cloud);
        e.getAffectedEntities().removeIf(entity -> getVictimUser(entity) != null && !applyPotionCombat(entity, attacker));
    }

    private boolean hasHarmfulEffects(Collection<PotionEffect> effects) {
        return effects.stream().anyMatch(effect -> effect.getType().getEffectCategory() == PotionEffectType.Category.HARMFUL);
    }

    private boolean applyPotionCombat(LivingEntity victim, Player attacker) {
        if (attacker == null || attacker.getUniqueId().equals(Objects.requireNonNull(getVictimUser(victim)).getUUID())) {
            return !isDamageProtected(victim);
        }
        return canCombat(victim, attacker);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSuccessfulSplash(PotionSplashEvent e) {
        if (!hasHarmfulEffects(e.getPotion().getEffects())) return;
        Player attacker = resolveAttacker(e.getPotion());
        for (LivingEntity victim : e.getAffectedEntities()) {
            if (e.getIntensity(victim) > 0) applySuccessfulCombat(victim, attacker);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSuccessfulCloud(AreaEffectCloudApplyEvent e) {
        AreaEffectCloud cloud = e.getEntity();
        Collection<PotionEffect> effects = new ArrayList<>(cloud.getCustomEffects());
        if (cloud.getBasePotionType() != null) effects.addAll(cloud.getBasePotionType().getPotionEffects());
        if (!hasHarmfulEffects(effects)) return;
        for (LivingEntity victim : e.getAffectedEntities()) {
            applySuccessfulCombat(victim, resolveAttacker(cloud));
        }
    }

    static void applySuccessfulCombat(LivingEntity victim, Player attacker) {
        User user = getVictimUser(victim);
        if (user == null || attacker == null || attacker.getUniqueId().equals(user.getUUID())) return;
        applyVictimCombatTimer(victim);
        applyCombatTimer(attacker, User.get(attacker.getUniqueId()));
    }

    private static void applyVictimCombatTimer(LivingEntity victim) {
        LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();
        User user = Objects.requireNonNull(getVictimUser(victim));
        if (victim instanceof Player player) {
            if (user.getTimer(Timer.Type.STUCK) != null) {
                user.removeTimer(Timer.Type.STUCK);
                player.sendMessage(C.chat(localeConfig.getString("commands.faction.stuck.cancel-hit")));
            }
            applyCombatTimer(player, user);
        } else {
            user.addTimer(Timer.Type.COMBAT, new Timer(Timer.Type.COMBAT));
            user.saveAsync();
        }
    }

    private static void applyCombatTimer(Player player, User user) {
        LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();
        if (user.getTimer(Timer.Type.HOME) != null) {
            user.removeTimer(Timer.Type.HOME);
            player.sendMessage(C.chat(localeConfig.getString("primary.teleport.cancel")));
        }
        boolean hadCombatTimer = user.hasTimer(Timer.Type.COMBAT);
        user.addTimer(Timer.Type.COMBAT, new Timer(Timer.Type.COMBAT));

        if (!hadCombatTimer) {
            player.sendMessage(C.chat(localeConfig.getTimerMessage("start", Timer.Type.COMBAT)
                    .replace("%time%", C.formatChatTime(Timer.Type.COMBAT.getLength()))));
        }
    }
}
