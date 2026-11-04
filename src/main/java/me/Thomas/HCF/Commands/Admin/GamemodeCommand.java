package me.Thomas.HCF.Commands.Admin;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class GamemodeCommand implements TabExecutor {
    private GameMode mode(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "survival", "s", "0" -> GameMode.SURVIVAL;
            case "creative", "c", "1" -> GameMode.CREATIVE;
            case "adventure", "a", "2" -> GameMode.ADVENTURE;
            case "spectator", "sp", "3" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.gamemode", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length < 1 || args.length > 2 || args.length == 1 && !(sender instanceof Player)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.gamemode.usage").replace("%alias%", alias)));
            return true;
        }
        GameMode mode = mode(args[0]);
        if (mode == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.gamemode.invalid")));
            return true;
        }
        if (!Permission.has(sender, "command.gamemode." + mode.name().toLowerCase(Locale.ROOT), "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        Player target = args.length == 1 ? (Player) sender : Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline() || sender instanceof Player p && !p.canSee(target)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        if (!target.equals(sender) && !Permission.has(sender, "command.gamemode.others", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        target.setGameMode(mode);
        if (target.getGameMode() != mode) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.gamemode.failed").replace("%player%", target.getName())));
            return true;
        }
        String name = Main.getInstance().getLocaleConfig().getString("commands.gamemode.modes." + mode.name().toLowerCase(Locale.ROOT));
        target.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.gamemode.received").replace("%mode%", name)));
        if (!target.equals(sender)) sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.gamemode.success")
                .replace("%player%", target.getName()).replace("%mode%", name)));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.gamemode", "admin") || args.length == 0) return List.of();
        String input = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) return Arrays.stream(GameMode.values()).map(mode -> mode.name().toLowerCase(Locale.ROOT))
                .filter(name -> Permission.has(sender, "command.gamemode." + name, "admin") && name.startsWith(input)).sorted().toList();
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream()
                .filter(target -> (!(sender instanceof Player p) || p.canSee(target))
                        && (target.equals(sender) || Permission.has(sender, "command.gamemode.others", "admin")))
                .map(Player::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        return List.of();
    }
}
