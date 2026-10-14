package me.Thomas.HCF.Events.Player.Combat;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class UserCombatLogoutEvent implements Listener {

    private final FileConfiguration messagesConfig = Main.getInstance().getMessagesConfig();
    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @EventHandler(priority = EventPriority.LOWEST)
    public void UserCombatLogout(PlayerQuitEvent e) {
        User user = User.get(e.getPlayer());
        user.removeTimer(Timer.Type.HOME);
        user.removeTimer(Timer.Type.STUCK);
    }
}
