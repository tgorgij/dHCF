package me.Thomas.HCF.Commands.User;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PingCommand implements TabExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (args.length > 1) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.ping.usage").replace("%alias%", alias)));
            return true;
        }
        Player target = args.length == 0 ? (sender instanceof Player p ? p : null) : Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        if (sender instanceof Player p && !p.canSee(target)) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.ping.success")
                .replace("%player%", target.getName()).replace("%ping%", Integer.toString(target.getPing()))));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        return List.of();
    }
}
