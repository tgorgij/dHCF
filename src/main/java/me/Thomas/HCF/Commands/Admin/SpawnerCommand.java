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
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SpawnerCommand implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.spawner", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 3) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.spawner.usage").replace("%alias%", alias)));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        int number;
        try {
            number = Integer.parseInt(args[2]);
            if (number <= 0) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.invalid-amount")));
            return true;
        }
        EntityType type = UserSpawnerEvent.getType(args[1]);
        if (type == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.spawner.invalid-type")));
            return true;
        }
        if (target.isDead() || User.get(target).isInStaffMode()) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.spawner.unavailable")));
            return true;
        }
        if (!UserSpawnerEvent.give(target, UserSpawnerEvent.createSpawner(type), number)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.spawner.no-space")));
            return true;
        }
        sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.spawner.success")
                .replace("%player%", target.getName()).replace("%amount%", Integer.toString(number)).replace("%type%", type.name().toLowerCase(Locale.ROOT))));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.spawner", "admin") || args.length == 0) return List.of();
        String input = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(player -> player.toLowerCase(Locale.ROOT).startsWith(input)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        if (args.length == 2) return Arrays.stream(EntityType.values()).filter(type -> UserSpawnerEvent.getType(type.name()) != null)
                .map(type -> type.name().toLowerCase(Locale.ROOT)).filter(type -> type.startsWith(input)).sorted().toList();
        return List.of();
    }
}
