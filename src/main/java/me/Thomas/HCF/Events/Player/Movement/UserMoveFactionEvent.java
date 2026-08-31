package me.Thomas.HCF.Events.Player.Movement;

import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Objects;

public class UserMoveFactionEvent implements Listener {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();
    private final String wilderness = localeConfig.getString("faction.wilderness");
    private final String warzone = localeConfig.getString("faction.warzone");
    private final String deathban = localeConfig.getString("faction.deathban");
    private final String nonDeathban = localeConfig.getString("faction.non-deathban");

    @EventHandler
    public void noneToFaction(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;
        Player p = e.getPlayer();
        User user = User.get(p);
        if (user.getLastEnteredFaction() != null) return;
        Faction faction = Faction.getAtLocation(to);
        if (faction == null) return;
        if (Faction.isInWilderness(from)) {
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("faction.moving.leaving")).replace("%deathban%", Objects.requireNonNull(deathban)).replace("%faction%", Objects.requireNonNull(wilderness))));
        } else {
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("faction.moving.leaving")).replace("%deathban%", Objects.requireNonNull(deathban)).replace("%faction%", Objects.requireNonNull(warzone))));
        }
        p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("faction.moving.entering")).replace("%deathban%", Objects.requireNonNull(faction.isDeathban() ? deathban : nonDeathban)).replace("%faction%", faction.getFormattedName(user))));
        user.setLastEnteredFaction(faction.getUUID());
    }

    @EventHandler
    public void factionToNone(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;
        Player p = e.getPlayer();
        User user = User.get(p);
        if (user.getLastEnteredFaction() == null) return;
        if (Faction.getAtLocation(to) != null) return;
        Faction faction = Faction.get(user.getLastEnteredFaction());
        if (faction == null) {
            user.setLastEnteredFaction(null);
            return;
        }
        p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("faction.moving.leaving")).replace("%deathban%", Objects.requireNonNull(faction.isDeathban() ? deathban : nonDeathban)).replace("%faction%", faction.getFormattedName(user))));
        if (Faction.isInWilderness(to)) {
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("faction.moving.entering")).replace("%deathban%", Objects.requireNonNull(deathban)).replace("%faction%", Objects.requireNonNull(wilderness))));
        } else {
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("faction.moving.entering")).replace("%deathban%", Objects.requireNonNull(deathban)).replace("%faction%", Objects.requireNonNull(warzone))));
        }
        user.setLastEnteredFaction(null);
    }

    @EventHandler
    public void factionToFaction(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;
        Player p = e.getPlayer();
        User user = User.get(p);
        if (user.getLastEnteredFaction() == null) return;
        Faction fromFaction = Faction.get(user.getLastEnteredFaction());
        Faction toFaction = Faction.getAtLocation(to);
        if (fromFaction == null) {
            user.setLastEnteredFaction(null);
            return;
        }
        if (toFaction == null || fromFaction.getUUID().equals(toFaction.getUUID())) return;
        p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("faction.moving.leaving")).replace("%deathban%", Objects.requireNonNull(fromFaction.isDeathban() ? deathban : nonDeathban)).replace("%faction%", fromFaction.getFormattedName(user))));
        p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("faction.moving.entering")).replace("%deathban%", Objects.requireNonNull(toFaction.isDeathban() ? deathban : nonDeathban)).replace("%faction%", toFaction.getFormattedName(user))));
        user.setLastEnteredFaction(toFaction.getUUID());
    }

    @EventHandler
    public void noneToNone(PlayerMoveEvent e) {
        Location from = e.getFrom();
        Location to = e.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;
        if (Faction.getAtLocation(from) != null || Faction.getAtLocation(to) != null) return;
        boolean fromWilderness = Faction.isInWilderness(from);
        boolean toWilderness = Faction.isInWilderness(to);
        if (fromWilderness == toWilderness) return;

        Player p = e.getPlayer();
        String leaving = fromWilderness ? wilderness : warzone;
        String entering = toWilderness ? wilderness : warzone;
        p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("faction.moving.leaving")).replace("%deathban%", Objects.requireNonNull(deathban)).replace("%faction%", Objects.requireNonNull(leaving))));
        p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("faction.moving.entering")).replace("%deathban%", Objects.requireNonNull(deathban)).replace("%faction%", Objects.requireNonNull(entering))));
    }
}
