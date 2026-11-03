package me.Thomas.HCF.Commands.User;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class IgnoreCommand implements TabExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.ignore.usage").replace("%alias%", alias)));
            return true;
        }
        User target = User.get(args[0]);
        if (target == null) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        if (target.getUUID().equals(p.getUniqueId())) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.ignore.self")));
            return true;
        }
        User user = User.get(p);
        boolean ignored = user.toggleIgnoredPlayer(target.getUUID());
        user.saveAsync();
        p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.ignore." + (ignored ? "added" : "removed"))
                .replace("%player%", target.getName())));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player p) || args.length != 1) return List.of();
        return Main.getInstance().getAllUsers().values().stream().filter(user -> !user.getUUID().equals(p.getUniqueId())).map(User::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
