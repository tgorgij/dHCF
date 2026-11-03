package me.Thomas.HCF.Commands.User;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class SeenCommand implements TabExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (args.length != 1) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.seen.usage").replace("%alias%", alias)));
            return true;
        }
        User target = User.get(args[0]);
        if (target == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        OfflinePlayer profile = Bukkit.getOfflinePlayer(target.getUUID());
        if (profile.isOnline()) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.seen.online").replace("%player%", target.getName())));
            return true;
        }
        long lastSeen = profile.getLastSeen();
        String path = lastSeen > 0 ? "commands.seen.offline" : "commands.seen.unknown";
        sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString(path).replace("%player%", target.getName())
                .replace("%time%", C.formatChatTime(Math.max(0L, System.currentTimeMillis() - lastSeen) / 1000L))));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length != 1) return List.of();
        return Main.getInstance().getAllUsers().values().stream().map(User::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
