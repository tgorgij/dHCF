package me.Thomas.HCF.Commands.User;

import me.Thomas.HCF.Events.Player.Connection.UserChatEvent;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TLCommand implements TabExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.tl.usage").replace("%alias%", alias)));
            return true;
        }
        Location location = p.getLocation();
        String message = Main.getInstance().getLocaleConfig().getString("commands.tl.location")
                .replace("%world%", location.getWorld().getName()).replace("%x%", Integer.toString(location.getBlockX()))
                .replace("%y%", Integer.toString(location.getBlockY())).replace("%z%", Integer.toString(location.getBlockZ()));
        UserChatEvent.sendFactionMessage(p, message);
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        return List.of();
    }
}
