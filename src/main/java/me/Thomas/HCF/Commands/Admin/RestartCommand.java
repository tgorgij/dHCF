package me.Thomas.HCF.Commands.Admin;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Utils.Timer.ServerTimer;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RestartCommand implements TabExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.restart", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.restart.usage").replace("%alias%", alias)));
            return true;
        }
        long seconds;
        try {
            seconds = Long.parseLong(args[0]);
            if (seconds < 1 || seconds > Integer.MAX_VALUE) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.invalid-amount")));
            return true;
        }
        if (Main.getInstance().hasServerTimer(ServerTimer.Type.RESTART)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.restart.running")));
            return true;
        }
        Main.getInstance().addServerTimer(new ServerTimer(ServerTimer.Type.RESTART, seconds));
        Bukkit.broadcast(C.chat(Main.getInstance().getLocaleConfig().getString("commands.restart.scheduled")
                .replace("%time%", C.formatChatTime(seconds))));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        return List.of();
    }
}
