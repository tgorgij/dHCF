package me.Thomas.HCF.Commands.Admin;

import me.Thomas.HCF.Events.Player.Movement.UserWorldBorderEvent;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class BorderCommand implements TabExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.border", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 2 || !List.of("overworld", "nether").contains(args[0].toLowerCase(Locale.ROOT))) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.border.usage").replace("%alias%", alias)));
            return true;
        }
        int radius;
        try {
            radius = Integer.parseInt(args[1]);
            if (radius < 1 || radius > 29_999_984) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.invalid-amount")));
            return true;
        }
        String dimension = args[0].toLowerCase(Locale.ROOT);
        Main.getInstance().getConfig().set("borders." + dimension, radius);
        Main.getInstance().saveConfig();
        World.Environment environment = dimension.equals("nether") ? World.Environment.NETHER : World.Environment.NORMAL;
        for (World world : Bukkit.getWorlds()) if (world.getEnvironment() == environment) UserWorldBorderEvent.apply(world);
        sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.border.success")
                .replace("%dimension%", dimension).replace("%radius%", Integer.toString(radius))));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.border", "admin") || args.length != 1) return List.of();
        return List.of("overworld", "nether").stream().filter(name -> name.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
