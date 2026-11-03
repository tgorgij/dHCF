package me.Thomas.HCF.Events.Player.Combat;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import io.papermc.paper.event.entity.EntityMoveEvent;
import me.Thomas.HCF.Main.Main;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent.TargetReason;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UserMobProtectionEvent implements Listener {
    private record Replacement(Mob mob, Goal<Mob> original, Goal<Mob> replacement, int priority) {}
    private final Map<UUID, Mob> targeting = new HashMap<>();
    private final Map<UUID, List<Replacement>> replacements = new HashMap<>();
    private final BukkitTask task;

    public UserMobProtectionEvent() {
        for (org.bukkit.World world : Bukkit.getWorlds()) for (LivingEntity entity : world.getLivingEntities()) {
            if (entity instanceof Mob mob) install(mob);
        }
        task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            for (Mob mob : List.copyOf(targeting.values())) {
                if (!mob.isValid() || mob.isDead() || !(mob.getTarget() instanceof Player p)) {
                    targeting.remove(mob.getUniqueId());
                    continue;
                }
                if (UserDamageEvent.isDamageProtected(p)) clearTarget(mob, p);
            }
        }, 1L, 5L);
    }

    private void clearTarget(Mob mob, Player p) {
        if (mob.getTarget() != null) mob.setTarget(null);
        mob.setAggressive(false);
        mob.getPathfinder().stopPathfinding();
        if (mob instanceof Wolf wolf) wolf.setAngry(false);
        if (mob instanceof PigZombie pigZombie) pigZombie.setAnger(0);
        if (mob instanceof Bee bee) bee.setAnger(0);
        if (mob instanceof Warden warden) warden.clearAnger(p);
        if (mob instanceof Enderman enderman) { enderman.setScreaming(false); enderman.setHasBeenStaredAt(false); }
        if (mob instanceof Creeper creeper) { creeper.setIgnited(false); creeper.setFuseTicks(0); }
        targeting.remove(mob.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getReason() == TargetReason.TEMPT || !(e.getEntity() instanceof Mob mob)
                || !(e.getTarget() instanceof Player p) || !UserDamageEvent.isDamageProtected(p)) return;
        e.setTarget(null);
        clearTarget(mob, p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTargetChanged(EntityTargetLivingEntityEvent e) {
        if (!(e.getEntity() instanceof Mob mob) || e.getReason() == TargetReason.TEMPT) return;
        if (e.getTarget() instanceof Player) targeting.put(mob.getUniqueId(), mob);
        else targeting.remove(mob.getUniqueId());
    }

    private boolean protectedAttacker(Mob mob) {
        EntityDamageEvent damage = mob.getLastDamageCause();
        if (damage == null) return false;
        Player p = UserDamageEvent.resolveAttacker(damage.getDamageSource().getCausingEntity());
        if (p == null && damage instanceof EntityDamageByEntityEvent hit) p = UserDamageEvent.resolveAttacker(hit.getDamager());
        return p != null && UserDamageEvent.isDamageProtected(p);
    }

    private boolean ignoreAvoidance(Mob mob) {
        boolean protectedNearby = false;
        double range = mob instanceof Rabbit ? 8 : 16;
        for (Entity entity : mob.getNearbyEntities(range, range, range)) {
            if (entity.getLocation().distanceSquared(mob.getLocation()) > range * range) continue;
            if (entity instanceof Player p && p.isOnline() && !p.isDead()
                    && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                if (!UserDamageEvent.isDamageProtected(p)) return false;
                protectedNearby = true;
            } else if (mob instanceof Rabbit && (entity instanceof Wolf || entity instanceof Monster)
                    || mob instanceof Fox && (entity instanceof Wolf || entity instanceof PolarBear)) return false;
        }
        return protectedNearby;
    }

    private class CalmGoal implements Goal<Mob> {
        private final Mob mob;
        private final Goal<Mob> original;
        private final boolean panic;
        private final GoalKey<Mob> key;

        private CalmGoal(Mob mob, Goal<Mob> original, boolean panic, int index) {
            this.mob = mob;
            this.original = original;
            this.panic = panic;
            key = GoalKey.of(Mob.class, new NamespacedKey(Main.getInstance(), "calm_" + original.getKey().getNamespacedKey().getKey() + "_" + index));
        }
        private boolean suppressed() { return panic ? protectedAttacker(mob) : ignoreAvoidance(mob); }
        @Override public boolean shouldActivate() { return original.shouldActivate() && !suppressed(); }
        @Override public boolean shouldStayActive() { return !suppressed() && original.shouldStayActive(); }
        @Override public void start() { original.start(); }
        @Override public void stop() {
            original.stop();
            if (suppressed()) mob.getPathfinder().stopPathfinding();
        }
        @Override public void tick() { original.tick(); }
        @Override public GoalKey<Mob> getKey() { return key; }
        @Override public EnumSet<GoalType> getTypes() { return original.getTypes(); }
    }

    private void install(Mob mob) {
        if (mob.getTarget() instanceof Player) targeting.put(mob.getUniqueId(), mob);
        if (replacements.containsKey(mob.getUniqueId())) return;
        List<Replacement> changed = new ArrayList<>();
        for (Goal<Mob> goal : List.copyOf(Bukkit.getMobGoals().getAllGoals(mob))) {
            NamespacedKey key = goal.getKey().getNamespacedKey();
            if (!key.getNamespace().equals("minecraft")) continue;
            String name = key.getKey();
            boolean panic = name.endsWith("panic");
            boolean avoid = mob instanceof Animals && !name.equals("wolf_avoid_entity")
                    && (name.equals("avoid_entity") || name.endsWith("_avoid_entity") || name.equals("panda_avoid"));
            if (!panic && !avoid) continue;
            Goal<Mob> replacement = new CalmGoal(mob, goal, panic, changed.size());
            int priority = panic ? 1 : 4;
            Bukkit.getMobGoals().removeGoal(mob, goal);
            Bukkit.getMobGoals().addGoal(mob, priority, replacement);
            changed.add(new Replacement(mob, goal, replacement, priority));
        }
        if (!changed.isEmpty()) replacements.put(mob.getUniqueId(), changed);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) { if (e.getEntity() instanceof Mob mob) install(mob); }
    @EventHandler
    public void onLoad(EntitiesLoadEvent e) { for (Entity entity : e.getEntities()) if (entity instanceof Mob mob) install(mob); }
    @EventHandler
    public void onUnload(EntitiesUnloadEvent e) {
        for (Entity entity : e.getEntities()) { targeting.remove(entity.getUniqueId()); replacements.remove(entity.getUniqueId()); }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        targeting.remove(e.getEntity().getUniqueId());
        replacements.remove(e.getEntity().getUniqueId());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(EntityMoveEvent e) {
        if (!(e.getEntity() instanceof Mob mob) || !protectedAttacker(mob)
                || !Boolean.TRUE.equals(mob.getMemory(MemoryKey.IS_PANICKING))) return;
        mob.setMemory(MemoryKey.IS_PANICKING, null);
        mob.getPathfinder().stopPathfinding();
        e.setCancelled(true);
    }
    @EventHandler
    public void onDisable(PluginDisableEvent e) {
        if (e.getPlugin() != Main.getInstance()) return;
        task.cancel();
        for (List<Replacement> goals : replacements.values()) for (Replacement entry : goals) {
            if (!entry.mob().isValid() || entry.mob().isDead()) continue;
            Bukkit.getMobGoals().removeGoal(entry.mob(), entry.replacement());
            Bukkit.getMobGoals().addGoal(entry.mob(), entry.priority(), entry.original());
        }
        replacements.clear();
        targeting.clear();
    }
}
