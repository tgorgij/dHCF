package me.Thomas.HCF.Events.Player.Combat;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.IO.Database.CombatLoggerStateStore;
import me.Thomas.HCF.IO.Database.CombatLoggerStateStore.State;
import me.Thomas.HCF.IO.Database.SaveSnapshot;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.Conquest.Conquest;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Items.SwordStats;
import me.Thomas.HCF.Utils.Deathban.Deathban;
import me.Thomas.HCF.Events.Player.Item.UserDeathSignEvent;
import me.Thomas.HCF.Utils.Combat.CombatLoggerInventory;
import me.Thomas.HCF.Utils.Combat.DTR;
import net.kyori.adventure.text.Component;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import io.papermc.paper.event.player.PlayerItemGroupCooldownEvent;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
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

import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
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

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();
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
        Instant deathTime = Instant.now();
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
        user.setLastInventory(ItemStack.serializeItemsAsBytes(inventory));
        user.setLastExperience(villager.getPersistentDataContainer().getOrDefault(
                new NamespacedKey(Main.getInstance(), "experience"), PersistentDataType.INTEGER, 0));
        Long duration = villager.getPersistentDataContainer().get(
                new NamespacedKey(Main.getInstance(), "deathban-duration"), PersistentDataType.LONG);
        Deathban.apply(user, duration == null
                ? Main.getInstance().getConfig().getLong("deathbans.default-duration", 3600L) : duration, villager.getLocation());
        recordDeathStatistics(e, user);
        addDeathSign(e, user, deathTime);
        String prefix = localeConfig.getString("events.player.death.combat-logger", "&7[Combat-Logger]");
        String victim = prefix + " " + getPlayerName(user);
        Component message = formatDeathMessage(e, victim);
        trackSwordKill(e, user, message);
        deathLightning(e);
        broadcastDeath(message);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void UserDeath(PlayerDeathEvent e) {
        Instant deathTime = Instant.now();
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
        user.setLastInventory(ItemStack.serializeItemsAsBytes(e.getEntity().getInventory().getContents()));
        user.setLastExperience(e.getEntity().calculateTotalExperiencePoints());
        Deathban.apply(user, Deathban.getDuration(e.getEntity()), e.getEntity().getLocation());
        recordDeathStatistics(e, user);
        addDeathSign(e, user, deathTime);
        Component deathMessage = formatDeathMessage(e, getPlayerName(user));
        trackSwordKill(e, user, deathMessage);
        deathLightning(e);
        e.deathMessage(null);
        broadcastDeath(deathMessage);
        // Let the death finish processing drops and statistics before disconnecting the player.
        Main.getInstance().getServer().getScheduler().runTask(Main.getInstance(), () -> {
            if (!Main.getInstance().isFeatureEnabled("deathbans")) return;
            Player player = e.getEntity();
            long remaining = Deathban.getRemainingSeconds(user.getDeathbanExpiry(), System.currentTimeMillis());
            if (!player.isOnline() || remaining == 0L) return;
            String message = localeConfig.getString("events.player.deathban",
                    "&cYou are deathbanned. &eYou can log back in in &c%time%&e.");
            player.kick(C.chat(message.replace("%time%", C.formatChatTime(remaining))));
        });
    }

    private void deathLightning(EntityDeathEvent e) {
        if (Main.getInstance().isFeatureEnabled("death-lightning")) {
            e.getEntity().getWorld().strikeLightningEffect(e.getEntity().getLocation());
        }
    }

    private void broadcastDeath(Component message) {
        if (Main.getInstance().isFeatureEnabled("death-messages")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (User.get(player).isDeathMessages()) player.sendMessage(message);
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    private void trackSwordKill(EntityDeathEvent e, User victim, Component message) {
        Player killer = getKiller(e);
        if (killer != null && !killer.getUniqueId().equals(victim.getUUID())) SwordStats.record(killer, message);
    }

    private void addDeathSign(EntityDeathEvent e, User victim, Instant deathTime) {
        Player killer = getKiller(e);
        EntityDamageEvent damage = e.getEntity().getLastDamageCause();
        e.getDrops().add(UserDeathSignEvent.create(victim.getName(), killer == null ? null : killer.getName(),
                damage == null ? null : damage.getCause(), deathTime));
    }

    private Player getKiller(EntityDeathEvent e) {
        Player killer = resolvePlayerAttacker(getAttackingEntity(e));
        // Preserve vanilla attribution when the final damage follows an earlier player hit.
        return killer == null ? e.getEntity().getKiller() : killer;
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
        victim.setKillstreak(0);
        Conquest conquest = Conquest.get();
        if (conquest != null) conquest.onDeath(victim);
        Player killer = getKiller(e);
        List<SaveSnapshot> snapshots = new ArrayList<>();
        Set<Faction> changedFactions = new LinkedHashSet<>();
        if (killer != null && !killer.getUniqueId().equals(victim.getUUID())) {
            User killerUser = User.get(killer);
            killerUser.setKills(killerUser.getKills() + 1);
            killerUser.setKillstreak((int) Math.min(Integer.MAX_VALUE, (long) killerUser.getKillstreak() + 1));
            transferKillBalance(victim, killerUser);
            SaveSnapshot killerSnapshot = killerUser.snapshot();
            if (killerSnapshot != null) snapshots.add(killerSnapshot);
            Faction faction = killerUser.hasFaction() ? Faction.get(killerUser.getFaction()) : null;
            if (faction != null && faction.getType() == Faction.Type.PLAYER && faction.hasMember(killerUser.getUUID())) {
                faction.addKill();
                faction.addPoints(Math.max(0, Main.getInstance().getConfig().getInt("factions.points.kill", 5)));
                changedFactions.add(faction);
            }
        }
        SaveSnapshot victimSnapshot = victim.snapshot();
        if (victimSnapshot != null) snapshots.add(victimSnapshot);
        if (victim.hasFaction()) {
            Faction faction = Faction.get(victim.getFaction());
            if (faction != null && faction.getType() == Faction.Type.PLAYER && faction.hasMember(victim.getUUID())) {
                boolean raidable = faction.isRaidable();
                DTR.onDeath(faction, System.currentTimeMillis());
                boolean becameRaidable = !raidable && faction.isRaidable();
                int penalty = Math.max(0, Main.getInstance().getConfig().getInt(
                        "factions.points." + (becameRaidable ? "raidable" : "death"), becameRaidable ? 15 : 5));
                faction.addPoints(-penalty);
                changedFactions.add(faction);
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
        for (Faction faction : changedFactions) {
            SaveSnapshot snapshot = faction.snapshot();
            if (snapshot != null) snapshots.add(snapshot);
        }
        // Save statistics, transferred money, faction points, and DTR together.
        Main.getInstance().getDatabaseManager().save(snapshots);
        if (killer != null && !killer.getUniqueId().equals(victim.getUUID())) giveKillstreakReward(killer);
    }

    private void giveKillstreakReward(Player player) {
        if (!Main.getInstance().getConfig().getBoolean("settings.features.killstreaks", false)) return;
        int kills = User.get(player).getKillstreak();
        for (String configured : Main.getInstance().getConfig().getStringList("killstreaks.rewards." + kills)) {
            String command = configured.replace("%player%", player.getName())
                    .replace("%kills%", Integer.toString(kills)).trim();
            if (command.startsWith("/")) command = command.substring(1);
            if (command.isBlank()) continue;
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (RuntimeException exception) {
                Main.getInstance().getLogger().log(Level.SEVERE, "Could not run killstreak reward: " + command, exception);
            }
        }
    }

    private void transferKillBalance(User victim, User killer) {
        double percentage = Main.getInstance().getConfig().getDouble("economy.kill-balance-percentage", 25.0);
        if (!Double.isFinite(percentage) || !Double.isFinite(victim.getBalance()) || !Double.isFinite(killer.getBalance())
                || victim.getBalance() <= 0 || killer.getBalance() < 0) return;
        BigDecimal balance = BigDecimal.valueOf(victim.getBalance());
        BigDecimal amount = balance.multiply(BigDecimal.valueOf(Math.clamp(percentage, 0.0, 100.0)))
                .movePointLeft(2).setScale(2, RoundingMode.DOWN);
        if (amount.signum() == 0) return;
        BigDecimal remaining = balance.subtract(amount);
        BigDecimal received = BigDecimal.valueOf(killer.getBalance()).add(amount);
        double newBalance = remaining.doubleValue();
        double newKillerBalance = received.doubleValue();
        if (!Double.isFinite(newBalance) || !Double.isFinite(newKillerBalance)
                || BigDecimal.valueOf(newBalance).compareTo(remaining) != 0
                || BigDecimal.valueOf(newKillerBalance).compareTo(received) != 0) return;
        victim.setBalance(newBalance);
        killer.setBalance(newKillerBalance);
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
