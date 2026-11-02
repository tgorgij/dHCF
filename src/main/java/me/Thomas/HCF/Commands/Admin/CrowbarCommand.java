package me.Thomas.HCF.Commands.Admin;

import me.Thomas.HCF.Events.Player.Item.UserSpawnerEvent;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class CrowbarCommand implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.crowbar", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.crowbar.usage").replace("%alias%", alias)));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        int number;
        try {
            number = Integer.parseInt(args[1]);
            if (number <= 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.invalid-amount")));
            return true;
        }
        if (target.isDead() || User.get(target).isInStaffMode()) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.crowbar.unavailable")));
            return true;
        }
        if (!UserSpawnerEvent.give(target, UserSpawnerEvent.createCrowbar(number), 1)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.crowbar.no-space")));
            return true;
        }
        sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.crowbar.success")
                .replace("%player%", target.getName()).replace("%uses%", Integer.toString(number))));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.crowbar", "admin") || args.length == 0) return List.of();
        String input = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(player -> player.toLowerCase(Locale.ROOT).startsWith(input)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        return List.of();
    }
}
