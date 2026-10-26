package me.Thomas.HCF.Events.Player.Movement;

import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import me.Thomas.HCF.Utils.Timer.StuckTimer;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;

public class UserMoveFactionEvent implements Listener {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();
    private final String wilderness = localeConfig.getString("faction.wilderness");
    private final String warzone = localeConfig.getString("faction.warzone");
    private final String deathban = localeConfig.getString("faction.deathban");
    private final String nonDeathban = localeConfig.getString("faction.non-deathban");

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e instanceof PlayerTeleportEvent) return;
        handleTransition(e);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        handleTransition(e);
    }

    private void handleTransition(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || from.getWorld() == null || to.getWorld() == null) return;
        Player p = e.getPlayer();
        User user = User.get(p);

        if (user.getTimer(Timer.Type.LOGOUT) != null && (!from.getWorld().equals(to.getWorld())
                || from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
            user.removeTimer(Timer.Type.LOGOUT);
            p.sendMessage(C.chat(localeConfig.getString("commands.logout.cancel-moved")));
        }

        if (hasChangedPosition(from, to) && user.getTimer(Timer.Type.HOME) != null) {
            user.removeTimer(Timer.Type.HOME);
            p.sendMessage(C.chat(localeConfig.getString("primary.teleport.cancel")));
        }
        if (user.getTimer(Timer.Type.STUCK) instanceof StuckTimer stuck && stuck.isOutsideRadius(to)) {
            user.removeTimer(Timer.Type.STUCK);
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.stuck.cancel-moved")));
        }

        if (from.getWorld().equals(to.getWorld())
                && from.getBlockX() == to.getBlockX()
                && from.getBlockZ() == to.getBlockZ()) return;

        Faction fromFaction = Faction.getAtLocation(from);
        Faction toFaction = Faction.getAtLocation(to);
        if (sameTerritory(from, to, fromFaction, toFaction)) return;

        sendMessage(p, user, "leaving", from, fromFaction);
        sendMessage(p, user, "entering", to, toFaction);
        user.setLastEnteredFaction(toFaction == null ? null : toFaction.getUUID());
    }

    private boolean hasChangedPosition(Location from, Location to) {
        return !from.getWorld().equals(to.getWorld())
                || from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY()
                || from.getBlockZ() != to.getBlockZ();
    }

    private boolean sameTerritory(Location from, Location to, Faction fromFaction, Faction toFaction) {
        if (fromFaction != null && toFaction != null) {
            return fromFaction.getUUID().equals(toFaction.getUUID());
        }
        if (fromFaction != null || toFaction != null) return false;
        return Faction.isInWilderness(from) == Faction.isInWilderness(to);
    }

    private void sendMessage(Player p, User user, String direction, Location location, Faction faction) {
        String factionName = faction == null
                ? Faction.isInWilderness(location) ? wilderness : warzone
                : faction.getFormattedName(user);
        String deathbanStatus = faction == null || faction.isDeathban() ? deathban : nonDeathban;
        p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("faction.moving." + direction))
                .replace("%deathban%", Objects.requireNonNull(deathbanStatus))
                .replace("%faction%", Objects.requireNonNull(factionName))));
    }
}
