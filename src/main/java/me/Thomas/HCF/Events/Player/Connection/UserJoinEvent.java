package me.Thomas.HCF.Events.Player.Connection;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Deathban.Deathban;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class UserJoinEvent implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onValidateLogin(PlayerConnectionValidateLoginEvent e) {
        if (!e.isAllowed()) return;
        PlayerProfile profile = null;
        if (e.getConnection() instanceof PlayerLoginConnection connection) {
            profile = connection.getAuthenticatedProfile();
        } else if (e.getConnection() instanceof PlayerConfigurationConnection connection) {
            profile = connection.getProfile();
        }
        if (profile == null || profile.getId() == null) return;

        // Existing users are loaded at startup; avoid creating first-time users before onJoin.
        User user = Main.getInstance().getAllUsers().get(profile.getId());
        if (user == null) return;
        long remaining = Deathban.getRemainingSeconds(user.getDeathbanExpiry(), System.currentTimeMillis());
        if (remaining > 0L) {
            String message = Main.getInstance().getLocaleConfig().getString("events.player.deathban",
                    "&cYou are deathbanned. &eYou can log back in in &c%time%&e.");
            e.kickMessage(C.chat(message.replace("%time%", C.formatChatTime(remaining))));
        } else if (user.getDeathbanExpiry() != 0L) {
            user.setDeathbanExpiry(0L);
            user.saveAsync();
        }
    }

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
