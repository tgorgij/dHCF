package me.Thomas.HCF.Events.Player.Combat;

import me.Thomas.HCF.Main.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class UserDeathEvent implements Listener {

    private final FileConfiguration messagesConfig = Main.getInstance().getMessagesConfig();
    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @EventHandler
    public void UserDeath(PlayerDeathEvent e) {
    }
}
