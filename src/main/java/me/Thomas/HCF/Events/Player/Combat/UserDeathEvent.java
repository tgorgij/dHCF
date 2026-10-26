package me.Thomas.HCF.Events.Player.Combat;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.IO.Database.CombatLoggerStateStore;
import me.Thomas.HCF.IO.Database.CombatLoggerStateStore.State;
import me.Thomas.HCF.IO.Database.SaveSnapshot;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Deathban.Deathban;
import me.Thomas.HCF.Utils.Combat.CombatLoggerInventory;
import me.Thomas.HCF.Utils.Combat.DTR;
import net.kyori.adventure.text.Component;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.papermc.paper.event.player.PlayerItemGroupCooldownEvent;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;
import java.util.logging.Level;

public class UserDeathEvent implements Listener {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();
    private final Map<UUID, Set<NamespacedKey>> itemCooldownGroups = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemCooldown(PlayerItemGroupCooldownEvent e) {
        Set<NamespacedKey> groups = itemCooldownGroups.computeIfAbsent(e.getPlayer().getUniqueId(), ignored -> new HashSet<>());
        if (e.getCooldown() > 0) groups.add(e.getCooldownGroup());
        else groups.remove(e.getCooldownGroup());
        if (groups.isEmpty()) itemCooldownGroups.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onCooldownPlayerQuit(PlayerQuitEvent e) {
        itemCooldownGroups.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRespawnCooldowns(PlayerPostRespawnEvent e) {
        if (e.getRespawnReason() == PlayerRespawnEvent.RespawnReason.END_PORTAL) return;
        Player player = e.getPlayer();
        User user = User.get(player);
        if (e.getRespawnReason() == PlayerRespawnEvent.RespawnReason.DEATH) user.setPvpProtectionPending(true);
        user.clearCooldowns();
        user.applyPendingPvpTimer();
        user.saveAsync();
        Set<NamespacedKey> groups = itemCooldownGroups.remove(player.getUniqueId());
        if (groups != null) {
            for (NamespacedKey group : groups) player.setCooldown(group, 0);
        }
        // Default cooldown groups use material keys; also clear any not observed by this listener.
        for (Material material : Material.values()) {
            if (!material.isLegacy() && player.getCooldown(material.getKey()) > 0) player.setCooldown(material.getKey(), 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void UserCombatLoggerDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Villager villager)) return;
        NamespacedKey player = new NamespacedKey(Main.getInstance(), "player");
        String playerId = villager.getPersistentDataContainer().get(player, PersistentDataType.STRING);
        if (playerId == null) return;
        UUID uuid;
        try {
            uuid = UUID.fromString(playerId);
        } catch (IllegalArgumentException exception) {
            return;
        }
        ItemStack[] inventory = CombatLoggerInventory.capture(villager);
        try {
            // Commit the clear-on-login marker before making the copied inventory available as drops.
            new CombatLoggerStateStore(Main.getInstance().getDatabaseManager()).markDead(uuid);
        } catch (RuntimeException exception) {
            e.setReviveHealth(1.0);
            e.setCancelled(true);
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not record combat-logger death", exception);
            return;
        }
        e.getDrops().clear();
        e.getDrops().addAll(CombatLoggerInventory.getDrops(inventory));
        User user = User.get(uuid);
        Long duration = villager.getPersistentDataContainer().get(
                new NamespacedKey(Main.getInstance(), "deathban-duration"), PersistentDataType.LONG);
        Deathban.apply(user, duration == null
                ? Main.getInstance().getConfig().getLong("deathbans.default-duration", 3600L) : duration, villager.getLocation());
        recordDeathStatistics(e, user);
        String prefix = localeConfig.getString("events.player.death.combat-logger", "&7[Combat-Logger]");
        String victim = prefix + " " + getPlayerName(user);
        Main.getInstance().getServer().broadcast(formatDeathMessage(e, victim));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void UserDeath(PlayerDeathEvent e) {
        State state = new CombatLoggerStateStore(Main.getInstance().getDatabaseManager()).get(e.getEntity().getUniqueId());
        if (state != null && state.died()) {
            // The logger's death was already counted; this death only opens its owner's respawn screen.
            e.getDrops().clear();
            e.setDroppedExp(0);
            e.setKeepLevel(false);
            e.setNewExp(0);
            e.setNewLevel(0);
            e.setNewTotalExp(0);
            e.deathMessage(null);
            return;
        }
        User user = User.get(e.getEntity());
        Deathban.apply(user, Deathban.getDuration(e.getEntity()), e.getEntity().getLocation());
        recordDeathStatistics(e, user);
        e.deathMessage(formatDeathMessage(e, getPlayerName(user)));
        // Let the death finish processing drops and statistics before disconnecting the player.
        Main.getInstance().getServer().getScheduler().runTask(Main.getInstance(), () -> {
            Player player = e.getEntity();
            long remaining = Deathban.getRemainingSeconds(user.getDeathbanExpiry(), System.currentTimeMillis());
            if (!player.isOnline() || remaining == 0L) return;
            String message = localeConfig.getString("events.player.deathban",
                    "&cYou are deathbanned. &eYou can log back in in &c%time%&e.");
            player.kick(C.chat(message.replace("%time%", C.formatChatTime(remaining))));
        });
    }

    private Component formatDeathMessage(EntityDeathEvent e, String victim) {
        EntityDamageEvent lastDamage = e.getEntity().getLastDamageCause();
        String cause = lastDamage == null ? "default"
                : lastDamage.getCause().name().toLowerCase(Locale.ROOT).replace('_', '-');
        String fallback = localeConfig.getString("events.player.death.default", "&c%player% &edied.");
        String message = localeConfig.getString("events.player.death." + cause, fallback);

        return C.chat(message
                .replace("%player%", victim)
                .replace("%attacker%", getAttackerName(getAttackingEntity(e))));
    }

    private Entity getAttackingEntity(EntityDeathEvent e) {
        EntityDamageEvent lastDamage = e.getEntity().getLastDamageCause();
        // The causing entity identifies the shooter or explosive owner when available.
        Entity attacker = e.getDamageSource().getCausingEntity();
        if (attacker == null) attacker = e.getDamageSource().getDirectEntity();
        if (attacker == null && lastDamage instanceof EntityDamageByEntityEvent entityDamage) {
            attacker = entityDamage.getDamager();
        }

        return attacker;
    }

    private void recordDeathStatistics(EntityDeathEvent e, User victim) {
        victim.setPvpProtectionPending(true);
        victim.setDeaths(victim.getDeaths() + 1);
        Player killer = resolvePlayerAttacker(getAttackingEntity(e));
        // Preserve vanilla kill attribution for deaths following earlier player damage (such as a fall).
        if (killer == null) killer = e.getEntity().getKiller();
        List<SaveSnapshot> snapshots = new ArrayList<>();
        SaveSnapshot victimSnapshot = victim.snapshot();
        if (victimSnapshot != null) snapshots.add(victimSnapshot);
        if (killer != null && !killer.getUniqueId().equals(victim.getUUID())) {
            User killerUser = User.get(killer);
            killerUser.setKills(killerUser.getKills() + 1);
            SaveSnapshot killerSnapshot = killerUser.snapshot();
            if (killerSnapshot != null) snapshots.add(killerSnapshot);
        }
        if (victim.hasFaction()) {
            Faction faction = Faction.get(victim.getFaction());
            if (faction != null && faction.getType() == Faction.Type.PLAYER && faction.hasMember(victim.getUUID())) {
                DTR.onDeath(faction, System.currentTimeMillis());
                SaveSnapshot factionSnapshot = faction.snapshot();
                if (factionSnapshot != null) snapshots.add(factionSnapshot);
                String dtr = C.getFormattedDTR(faction);
                for (String line : Main.getInstance().getMessagesConfig().getStringList("factions.member.death")) {
                    Component message = C.chat(line.replace("%player%", victim.getName()).replace("%dtr%", dtr));
                    for (UUID uuid : faction.getMembers().keySet()) {
                        Player member = Bukkit.getPlayer(uuid);
                        if (member != null) member.sendMessage(message);
                    }
                }
            }
        }
        // Keep deaths, kills, and the faction's DTR/freeze update in the same database transaction.
        Main.getInstance().getDatabaseManager().save(snapshots);
    }

    private Player resolvePlayerAttacker(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        if (entity instanceof TNTPrimed tnt) return resolvePlayerAttacker(tnt.getSource());
        if (entity instanceof Tameable tameable && tameable.getOwner() instanceof Player player) return player;
        if (entity instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) return player;
        return null;
    }

    private String getAttackerName(Entity attacker) {
        if (attacker instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            attacker = shooter;
        } else if (attacker instanceof TNTPrimed tnt && tnt.getSource() != null) {
            attacker = tnt.getSource();
        }
        if (attacker == null) return "&ean unknown attacker";
        return attacker instanceof Player player ? getPlayerName(User.get(player))
                : "&ea &c" + attacker.getName() + "&e";
    }

    private String getPlayerName(User user) {
        String format = Main.getInstance().getConfig().getString("factions.player-format", "&c%player%&7[&c%kills%&7]");
        return format.replace("%player%", "&c" + user.getName())
                .replace("%kills%", Integer.toString(user.getKills())) + "&e";
    }
}
