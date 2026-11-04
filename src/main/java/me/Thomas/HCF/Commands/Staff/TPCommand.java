package me.Thomas.HCF.Commands.Staff;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class TPCommand implements TabExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.tp", "staff") && !Permission.has(sender, "command.tp", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length < 1 || args.length > 3) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.tp.usage").replace("%alias%", alias)));
            return true;
        }
        if (args.length != 2 && !(sender instanceof Player)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        Player source = args.length == 2 ? Bukkit.getPlayerExact(args[0]) : (Player) sender;
        Player target = args.length == 3 ? null : Bukkit.getPlayerExact(args[args.length - 1]);
        if (source == null || !source.isOnline() || args.length != 3 && (target == null || !target.isOnline())
                || sender instanceof Player p && (!p.canSee(source) || target != null && !p.canSee(target))) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        Location destination;
        if (args.length == 3) {
            destination = source.getLocation();
            try {
                double x = Double.parseDouble(args[0]), y = Double.parseDouble(args[1]), z = Double.parseDouble(args[2]);
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                        || Math.abs(x) > 29_999_984 || Math.abs(z) > 29_999_984 || Math.abs(y) > 20_000_000) throw new NumberFormatException();
                destination.setX(x);
                destination.setY(y);
                destination.setZ(z);
            } catch (NumberFormatException exception) {
                sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.tp.invalid-coordinates")));
                return true;
            }
        } else destination = target.getLocation();
        if (!source.teleport(destination, TeleportCause.COMMAND)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.tp.failed").replace("%player%", source.getName())));
            return true;
        }
        source.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.tp.teleported")));
        if (!source.equals(sender)) sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.tp.success")
                .replace("%player%", source.getName()).replace("%target%", target.getName())));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if ((!Permission.has(sender, "command.tp", "staff") && !Permission.has(sender, "command.tp", "admin"))
                || args.length < 1 || args.length > 2) return List.of();
        return Bukkit.getOnlinePlayers().stream().filter(target -> !(sender instanceof Player p) || p.canSee(target))
                .map(Player::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[args.length - 1].toLowerCase(Locale.ROOT)))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
