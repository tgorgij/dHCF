package me.Thomas.HCF.Events.Player.Connection;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class UserQuitEvent implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onQuit(PlayerQuitEvent e) {
        e.quitMessage(null);
        Player p = e.getPlayer();
        User user = User.get(p);
        user.setName(p.getName());
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction != null) faction.broadcastMemberConnection(p, false);
        user.pauseTimers();
        Main.getInstance().removeUser(p.getUniqueId());
    }
}
