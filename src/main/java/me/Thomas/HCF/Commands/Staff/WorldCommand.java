package me.Thomas.HCF.Commands.Staff;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class WorldCommand implements TabExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.world", "staff") && !Permission.has(sender, "command.world", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        if (args.length != 1) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.world.usage").replace("%alias%", alias)));
            return true;
        }
        World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.world.not-found")));
            return true;
        }
        Location destination = p.getLocation();
        destination.setWorld(world);
        if (!p.teleport(destination, TeleportCause.COMMAND)) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.world.failed")));
            return true;
        }
        p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.world.success").replace("%world%", world.getName())));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player) || args.length != 1
                || !Permission.has(sender, "command.world", "staff") && !Permission.has(sender, "command.world", "admin")) return List.of();
        return Bukkit.getWorlds().stream().map(World::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
