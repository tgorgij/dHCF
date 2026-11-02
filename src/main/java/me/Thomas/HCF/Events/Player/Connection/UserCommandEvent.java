package me.Thomas.HCF.Events.Player.Connection;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

public class UserCommandEvent implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!User.get(p).hasTimer(Timer.Type.COMBAT)) return;
        String input = e.getMessage().substring(1).strip().toLowerCase(Locale.ROOT);
        if (input.isEmpty()) return;
        String[] args = input.split("\\s+");
        for (String blocked : Main.getInstance().getConfig().getStringList("combat.blocked-commands")) {
            String entry = blocked.strip().replaceFirst("^/", "").toLowerCase(Locale.ROOT);
            if (entry.isEmpty()) continue;
            String[] tokens = entry.split("\\s+");
            if (args.length < tokens.length || !name(args[0]).equals(name(tokens[0]))) continue;
            boolean matches = true;
            for (int i = 1; i < tokens.length; i++) {
                if (!args[i].equals(tokens[i])) { matches = false; break; }
            }
            if (!matches) continue;
            e.setCancelled(true);
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.combat-tagged")));
            return;
        }
    }

    private String name(String label) {
        Command command = Bukkit.getCommandMap().getCommand(label);
        String name = command == null ? label : command.getName();
        return name.substring(name.indexOf(':') + 1).toLowerCase(Locale.ROOT);
    }
}
