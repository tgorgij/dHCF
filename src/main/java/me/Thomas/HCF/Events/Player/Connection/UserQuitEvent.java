package me.Thomas.HCF.Events.Player.Connection;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class UserQuitEvent implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        User user = User.get(p);
        user.setName(p.getName());
        user.pauseTimers();
        user.save();
        Main.getInstance().removeUser(p.getUniqueId());
    }
}
