package me.Thomas.HCF.Events.World;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.NPC;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.entity.TrialSpawnerSpawnEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class MobEvent implements Listener {

    private final NamespacedKey countKey = new NamespacedKey(Main.getInstance(), "mob-stack");
    private final NamespacedKey playerKey = new NamespacedKey(Main.getInstance(), "player");

    private int count(Mob mob) {
        return Math.max(1, mob.getPersistentDataContainer().getOrDefault(countKey, PersistentDataType.INTEGER, 1));
    }

    private boolean exempt(Entity entity) {
        return entity instanceof Player || entity instanceof Item || entity instanceof ExperienceOrb
                || entity instanceof Projectile || entity.getPersistentDataContainer().has(playerKey, PersistentDataType.STRING);
    }

    private boolean stackable(Mob mob) {
        return !exempt(mob) && !(mob instanceof NPC) && !(mob instanceof Boss) && !(mob instanceof Tameable) && !mob.isLeashed() && !mob.isInsideVehicle() && mob.getPassengers().isEmpty() && (mob.customName() == null || mob.getPersistentDataContainer().has(countKey, PersistentDataType.INTEGER));
    }

    private boolean matches(Mob first, Mob second) {
        if (first.getType() != second.getType()) return false;
        switch (first) {
            case Ageable age when second instanceof Ageable other && age.isAdult() != other.isAdult() -> {
                return false;
            }
            case Slime slime when second instanceof Slime other && slime.getSize() != other.getSize() -> {
                return false;
            }
            case Creeper creeper when second instanceof Creeper other && creeper.isPowered() != other.isPowered() -> {
                return false;
            }
            case Sheep sheep when second instanceof Sheep other && (sheep.getColor() != other.getColor() || sheep.isSheared() != other.isSheared()) -> {
                return false;
            }
            default -> {
            }
        }
        EntityEquipment equipment = first.getEquipment();
        EntityEquipment other = second.getEquipment();
        return Arrays.equals(equipment.getArmorContents(), other.getArmorContents())
                && Objects.equals(equipment.getItemInMainHand(), other.getItemInMainHand())
                && Objects.equals(equipment.getItemInOffHand(), other.getItemInOffHand());
    }

    private void setCount(Mob mob, int amount) {
        mob.getPersistentDataContainer().set(countKey, PersistentDataType.INTEGER, amount);
        mob.setCanPickupItems(false);
        Component name = C.chat(Main.getInstance().getLocaleConfig().getString("events.mob.stack-name")
                .replace("%amount%", Integer.toString(amount)))
                .replaceText(builder -> builder.matchLiteral("%mob%")
                        .replacement(Component.translatable(mob.getType().translationKey())));
        mob.customName(name);
        mob.setCustomNameVisible(true);
    }

    private boolean freshSpawn(CreatureSpawnEvent.SpawnReason reason) {
        return switch (reason) {
            case NATURAL, SPAWNER, TRIAL_SPAWNER, SPAWNER_EGG, DISPENSE_EGG, EGG, BREEDING,
                    COMMAND, CUSTOM, SLIME_SPLIT, REINFORCEMENTS -> true;
            default -> false;
        };
    }

    private boolean stack(Mob mob) {
        // Only newly spawned single mobs join a stack; existing stacks never merge.
        if (mob.getPersistentDataContainer().has(countKey, PersistentDataType.INTEGER) || !stackable(mob)) return false;
        double radius = Main.getInstance().getConfig().getDouble("mob-stacking.radius", 8.0);
        if (!Double.isFinite(radius) || radius <= 0) return false;
        Location location = mob.getLocation();
        Mob closest = null;
        double distance = radius * radius;
        for (Entity entity : mob.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Mob nearby) || entity.getUniqueId().equals(mob.getUniqueId())
                    || !nearby.isValid() || nearby.isDead() || count(nearby) == Integer.MAX_VALUE
                    || !stackable(nearby) || !matches(mob, nearby)) continue;
            double squared = location.distanceSquared(nearby.getLocation());
            if (squared <= distance) {
                distance = squared;
                closest = nearby;
            }
        }
        if (closest == null) return false;
        setCount(closest, count(closest) + 1);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent e) {
        // Spawner pre-events precede the actual CreatureSpawnEvent.
        if (e instanceof SpawnerSpawnEvent || e instanceof TrialSpawnerSpawnEvent || exempt(e.getEntity())) return;
        if (e instanceof CreatureSpawnEvent spawn && freshSpawn(spawn.getSpawnReason()) && e.getEntity() instanceof Mob mob
                && Main.getInstance().isFeatureEnabled("mob-stacking") && stack(mob)) {
            e.setCancelled(true);
            return;
        }
        if (!Main.getInstance().isFeatureEnabled("entity-limits")) return;
        int limit = Main.getInstance().getConfig().getInt("entity-limits.per-chunk", 100);
        if (limit <= 0) return;
        int entities = 0;
        for (Entity entity : e.getLocation().getChunk().getEntities()) {
            if (entity.getUniqueId().equals(e.getEntity().getUniqueId()) || entity.isDead() || exempt(entity)) continue;
            if (++entities >= limit) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Mob mob) || exempt(mob) || count(mob) <= 1) return;
        AttributeInstance health = mob.getAttribute(Attribute.MAX_HEALTH);
        if (health == null || health.getValue() <= 0) return;
        List<ItemStack> drops = e.getDrops().stream().map(ItemStack::clone).toList();
        int experience = e.getDroppedExp();
        e.setReviveHealth(health.getValue());
        e.setCancelled(true);
        setCount(mob, count(mob) - 1);
        // Cancelling death suppresses vanilla drops, so release one mob's loot and XP ourselves.
        e.getDrops().clear();
        e.setDroppedExp(0);
        for (ItemStack item : drops) mob.getWorld().dropItemNaturally(mob.getLocation(), item);
        if (experience > 0) {
            mob.getWorld().spawn(mob.getLocation(), ExperienceOrb.class, orb -> orb.setExperience(experience));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) {
        if (e.getEntity() instanceof Mob mob && count(mob) > 1) e.setCancelled(true);
    }
}
