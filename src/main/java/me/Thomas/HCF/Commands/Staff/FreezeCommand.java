package me.Thomas.HCF.Commands.Staff;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FreezeCommand implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.freeze", "staff")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }

        if (args.length != 1) { Main.getInstance().getStaffMode().message(sender, "freeze-usage", "%alias%", alias); return true; }
        if (args[0].equalsIgnoreCase("all")) { Main.getInstance().getStaffMode().toggleFreezeAll(sender); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) { sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player"))); return true; }
        Main.getInstance().getStaffMode().toggleFreeze(sender, target);
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.freeze", "staff") || args.length != 1) return List.of();
        List<String> names = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!(sender instanceof Player p) || p.canSee(target)) names.add(target.getName());
        }
        names.add("all");
        String input = args[0].toLowerCase(Locale.ROOT);
        return names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
