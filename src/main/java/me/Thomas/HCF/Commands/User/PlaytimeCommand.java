package me.Thomas.HCF.Commands.User;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class PlaytimeCommand implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (args.length > 1) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.playtime.usage").replace("%alias%", alias)));
            return true;
        }
        if (args.length == 0 && !(sender instanceof Player)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        User target = args.length == 0 ? User.get((Player) sender) : User.get(args[0]);
        if (target == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        long seconds = Math.max(0L, Bukkit.getOfflinePlayer(target.getUUID()).getStatistic(Statistic.PLAY_ONE_MINUTE)) / 20L;
        sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.playtime." + (args.length == 0 ? "self" : "other"))
                .replace("%player%", target.getName()).replace("%time%", C.formatChatTime(seconds))));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length != 1) return List.of();
        return Main.getInstance().getAllUsers().values().stream().map(User::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
