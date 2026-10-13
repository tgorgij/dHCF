package me.Thomas.HCF.Events.Player.Connection;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class UserJoinEvent implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!Main.getInstance().getAllUsers().containsKey(p.getUniqueId())) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.first-join")));
        }
        Main.getInstance().addUser(p);
        User user = User.get(p);
        user.unpauseTimers();
    }
}
