package me.Thomas.HCF.Commands.Staff;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class HealCommand implements TabExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.heal", "staff")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length > 1) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.heal.usage").replace("%alias%", alias)));
            return true;
        }
        Player target = args.length == 0 ? (sender instanceof Player p ? p : null) : Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        if (target.isDead()) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.heal.dead")));
            return true;
        }
        target.setHealth(target.getAttribute(Attribute.MAX_HEALTH).getValue());
        target.setFireTicks(0);
        target.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.heal.received")));
        if (!target.equals(sender)) sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.heal.success").replace("%player%", target.getName())));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.heal", "staff") || args.length != 1) return List.of();
        return Bukkit.getOnlinePlayers().stream().filter(p -> !(sender instanceof Player viewer) || viewer.canSee(p)).map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
