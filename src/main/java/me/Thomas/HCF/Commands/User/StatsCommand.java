package me.Thomas.HCF.Commands.User;

import me.Thomas.HCF.Events.Player.Interact.UserStatsEvent;
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

public class StatsCommand implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        if (args.length > 1) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.stats.usage").replace("%alias%", alias)));
            return true;
        }
        User target = args.length == 0 ? User.get(p) : User.get(args[0]);
        if (target == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        UserStatsEvent.open(p, target);
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player) || args.length != 1) return List.of();
        return Main.getInstance().getAllUsers().values().stream().map(User::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
