package me.Thomas.HCF.Events.Player.Combat;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.IO.Database.CombatLoggerStateStore;
import me.Thomas.HCF.IO.Database.CombatLoggerStateStore.State;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Timer.Timer;
import me.Thomas.HCF.Utils.Deathban.Deathban;
import me.Thomas.HCF.Utils.Combat.CombatLoggerInventory;
import me.Thomas.HCF.Utils.Combat.CombatLoggerEffects;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class UserCombatLogoutEvent implements Listener {

    private final CombatLoggerStateStore stateStore;
    private final Map<UUID, State> reconnectStates = new HashMap<>();
    private final Map<UUID, BukkitTask> despawnTasks = new HashMap<>();
    private final Map<UUID, Chunk> loggerChunks = new HashMap<>();
    private final String session = UUID.randomUUID().toString();
    private boolean shuttingDown;

    public UserCombatLogoutEvent() {
        stateStore = new CombatLoggerStateStore(Main.getInstance().getDatabaseManager());
        stateStore.discardSnapshots();
        removeSavedCombatLoggers();
    }

    @EventHandler
    public void onDisable(PluginDisableEvent e) {
        if (e.getPlugin() != Main.getInstance()) return;
        shutdown();
    }

    public void removeSavedCombatLoggers() {
        for (World world : Main.getInstance().getServer().getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                removeSavedCombatLogger(villager);
            }
        }
    }

    @EventHandler
    public void onSavedCombatLoggersLoad(EntitiesLoadEvent e) {
        for (Entity entity : e.getEntities()) {
            if (entity instanceof Villager villager) removeSavedCombatLogger(villager);
        }
    }

    private void removeSavedCombatLogger(Villager villager) {
        if (villager.isDead() || !isCombatLogger(villager)
                || session.equals(villager.getPersistentDataContainer().get(
                new NamespacedKey(Main.getInstance(), "combat-logger-session"), PersistentDataType.STRING))) return;
        cancelDespawn(villager);
        // A stale world entity must never restore state from an earlier server session.
        villager.remove();
    }

    @EventHandler
    public void UserCombatReconnect(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        NamespacedKey player = new NamespacedKey(Main.getInstance(), "player");
        UUID uuid = p.getUniqueId();
        // Only unapplied deaths survive a restart. Living logger snapshots are session-only.
        State state = stateStore.get(uuid);
        if (state == null) state = reconnectStates.get(uuid);
        if (state != null && state.died()) {
            p.getInventory().clear();
            p.setItemOnCursor(null);
            p.saveData();
            // The world-spawn move happens only when the player actually respawns.
            if (!p.isDead()) p.setHealth(0);
        }
        boolean restored = false;
        for (World world : Main.getInstance().getServer().getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (!villager.isDead() && uuid.toString().equals(villager.getPersistentDataContainer()
                        .get(player, PersistentDataType.STRING))) {
                    if (!session.equals(villager.getPersistentDataContainer().get(
                            new NamespacedKey(Main.getInstance(), "combat-logger-session"), PersistentDataType.STRING))) {
                        removeSavedCombatLogger(villager);
                        continue;
                    }
                    if (state == null || !state.died()) {
                        restoreCombatLogger(p, villager);
                        p.saveData();
                    }
                    cancelDespawn(villager);
                    villager.remove();
                    restored = true;
                }
            }
        }
        if (!restored && state != null && !state.died()) {
            restoreState(p, state);
            p.saveData();
        }
        reconnectStates.remove(uuid);
    }

    private void restoreCombatLogger(Player player, Villager villager) {
        restoreState(player, captureState(villager));
    }

    private void restoreState(Player player, State state) {
        PlayerInventory inventory = player.getInventory();
        inventory.setContents(ItemStack.deserializeItemsFromBytes(state.inventory()));
        inventory.setHeldItemSlot(state.heldSlot());
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.addPotionEffects(CombatLoggerEffects.deserialize(state.effects()));
        double maxHealth = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();
        player.setHealth(Math.min(state.health(), maxHealth));
        restoreAbsorption(player, state.absorption());
    }

    private State captureState(Villager villager) {
        return new State(ItemStack.serializeItemsAsBytes(CombatLoggerInventory.capture(villager)),
                CombatLoggerInventory.getHeldSlot(villager), villager.getHealth(), false, villager.getAbsorptionAmount(),
                CombatLoggerEffects.serialize(villager.getActivePotionEffects()));
    }

    private void restoreAbsorption(org.bukkit.entity.LivingEntity entity, double amount) {
        AttributeInstance maximum = Objects.requireNonNull(entity.getAttribute(Attribute.MAX_ABSORPTION));
        if (amount > maximum.getValue()) {
            maximum.setBaseValue(maximum.getBaseValue() + amount - maximum.getValue());
        }
        entity.setAbsorptionAmount(amount);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombatLoggerRespawn(PlayerRespawnEvent e) {
        State state = stateStore.get(e.getPlayer().getUniqueId());
        if (state != null && state.died()) e.setRespawnLocation(e.getPlayer().getWorld().getSpawnLocation());
    }

    @EventHandler
    public void onCombatLoggerPostRespawn(PlayerPostRespawnEvent e) {
        Player player = e.getPlayer();
        State state = stateStore.get(player.getUniqueId());
        if (state == null || !state.died()) return;
        player.getInventory().clear();
        player.setItemOnCursor(null);
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setAbsorptionAmount(0);
        player.setHealth(Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue());
        player.setExp(0);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.setFoodLevel(20);
        player.setSaturation(5);
        player.setExhaustion(0);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setFallDistance(0);
        player.setRemainingAir(player.getMaximumAir());
        player.saveData();
        stateStore.delete(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void UserCombatLogout(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (shuttingDown) return;
        User user = User.get(p.getUniqueId());
        user.removeTimer(Timer.Type.HOME);
        user.removeTimer(Timer.Type.STUCK);
        user.removeTimer(Timer.Type.LOGOUT);
        if (p.isDead() || !user.hasTimer(Timer.Type.COMBAT)) return;
        Villager villager = p.getWorld().spawn(p.getLocation(), Villager.class);
        villager.setPersistent(false);
        villager.setAI(false);
        villager.setCanPickupItems(false);
        villager.setProfession(Villager.Profession.NITWIT);
        NamespacedKey player = new NamespacedKey(Main.getInstance(), "player");
        villager.getPersistentDataContainer().set(player, PersistentDataType.STRING, p.getUniqueId().toString());
        villager.getPersistentDataContainer().set(new NamespacedKey(Main.getInstance(), "combat-logger-session"),
                PersistentDataType.STRING, session);
        villager.getPersistentDataContainer().set(new NamespacedKey(Main.getInstance(), "deathban-duration"),
                PersistentDataType.LONG, Deathban.getDuration(p));

        AttributeInstance playerMaxHealth = Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH));
        AttributeInstance villagerMaxHealth = Objects.requireNonNull(villager.getAttribute(Attribute.MAX_HEALTH));
        villagerMaxHealth.setBaseValue(playerMaxHealth.getBaseValue());

        PlayerInventory inventory = p.getInventory();
        // Villagers have only eight inventory slots; retain every player slot in persistent data.
        villager.getPersistentDataContainer().set(new NamespacedKey(Main.getInstance(), "inventory"),
                PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(inventory.getContents()));
        villager.getPersistentDataContainer().set(new NamespacedKey(Main.getInstance(), "held-slot"),
                PersistentDataType.INTEGER, inventory.getHeldItemSlot());

        EntityEquipment equipment = Objects.requireNonNull(villager.getEquipment());
        equipment.setArmorContents(copyArmor(inventory.getArmorContents()));
        equipment.setItemInMainHand(inventory.getItemInMainHand().clone());
        equipment.setItemInOffHand(inventory.getItemInOffHand().clone());
        villager.addPotionEffects(p.getActivePotionEffects());
        for (AttributeModifier modifier : playerMaxHealth.getModifiers()) {
            if (villagerMaxHealth.getModifier(modifier.getKey()) == null) villagerMaxHealth.addModifier(modifier);
        }
        villager.setHealth(p.getHealth());
        restoreAbsorption(villager, p.getAbsorptionAmount());

        scheduleDespawn(villager);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatLoggerHit(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Villager villager && isCombatLogger(villager)) {
            scheduleDespawn(villager);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatLoggerDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Villager villager) cancelDespawn(villager);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombatLoggerTransform(EntityTransformEvent e) {
        if (e.getEntity() instanceof Villager villager && isCombatLogger(villager)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatLoggerMove(EntityMoveEvent e) {
        if (e.getEntity() instanceof Villager villager && despawnTasks.containsKey(villager.getUniqueId())
                && (!Objects.equals(e.getFrom().getWorld(), e.getTo().getWorld())
                || (e.getFrom().getBlockX() >> 4) != (e.getTo().getBlockX() >> 4)
                || (e.getFrom().getBlockZ() >> 4) != (e.getTo().getBlockZ() >> 4))) {
            updateChunk(villager, e.getTo().getChunk());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCombatLoggerTeleport(EntityTeleportEvent e) {
        if (e.getEntity() instanceof Villager villager && despawnTasks.containsKey(villager.getUniqueId()) && e.getTo() != null) {
            updateChunk(villager, e.getTo().getChunk());
        }
    }

    private void updateChunk(Villager villager, Chunk chunk) {
        if (chunk.equals(loggerChunks.get(villager.getUniqueId()))) return;
        chunk.addPluginChunkTicket(Main.getInstance());
        Chunk previous = loggerChunks.put(villager.getUniqueId(), chunk);
        if (previous != null && !loggerChunks.containsValue(previous)) previous.removePluginChunkTicket(Main.getInstance());
    }

    public void shutdown() {
        shuttingDown = true;
        for (World world : Main.getInstance().getServer().getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (!isCombatLogger(villager)) continue;
                cancelDespawn(villager);
                villager.remove();
            }
        }
        for (BukkitTask task : despawnTasks.values()) task.cancel();
        despawnTasks.clear();
        for (Chunk chunk : new java.util.HashSet<>(loggerChunks.values())) chunk.removePluginChunkTicket(Main.getInstance());
        loggerChunks.clear();
        reconnectStates.clear();
    }

    private boolean isCombatLogger(Villager villager) {
        return villager.getPersistentDataContainer().has(new NamespacedKey(Main.getInstance(), "player"),
                PersistentDataType.STRING);
    }

    private void cancelDespawn(Villager villager) {
        BukkitTask task = despawnTasks.remove(villager.getUniqueId());
        if (task != null) task.cancel();
        releaseChunk(villager);
    }

    private void releaseChunk(Villager villager) {
        Chunk chunk = loggerChunks.remove(villager.getUniqueId());
        if (chunk != null && !loggerChunks.containsValue(chunk)) {
            chunk.removePluginChunkTicket(Main.getInstance());
        }
    }

    private void scheduleDespawn(Villager villager) {
        cancelDespawn(villager);
        // Keep the logger loaded until it despawns or its player reconnects.
        updateChunk(villager, villager.getLocation().getChunk());
        long despawnSeconds = Math.clamp(Main.getInstance().getConfig().getLong("combat-logger.despawn-after", 30L),
                1L, Long.MAX_VALUE / 20L);
        BukkitTask task = Main.getInstance().getServer().getScheduler().runTaskLater(Main.getInstance(), () -> {
            despawnTasks.remove(villager.getUniqueId());
            if (villager.isDead() || !villager.isValid()) {
                releaseChunk(villager);
                return;
            }
            UUID uuid = UUID.fromString(Objects.requireNonNull(villager.getPersistentDataContainer().get(
                    new NamespacedKey(Main.getInstance(), "player"), PersistentDataType.STRING)));
            reconnectStates.put(uuid, captureState(villager));
            // Removal does not fire EntityDeathEvent, so expiry never applies a deathban.
            villager.remove();
            releaseChunk(villager);
        }, despawnSeconds * 20L);
        despawnTasks.put(villager.getUniqueId(), task);
    }

    private ItemStack[] copyArmor(ItemStack[] contents) {
        ItemStack[] armor = contents.clone();
        for (int slot = 0; slot < armor.length; slot++) {
            if (armor[slot] != null) armor[slot] = armor[slot].clone();
        }
        return armor;
    }
}
