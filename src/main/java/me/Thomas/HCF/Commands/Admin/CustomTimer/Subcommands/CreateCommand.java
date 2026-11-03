package me.Thomas.HCF.Commands.Admin.CustomTimer.Subcommands;

import me.Thomas.HCF.Commands.Admin.CustomTimer.CustomTimerCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Utils.Timer.ServerTimer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CreateCommand implements Subcommand {
    @Override
    public String getName() { return "create"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.customtimer", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        boolean user = args.length > 0 && args[0].equalsIgnoreCase("user");
        int index = user ? 2 : 1;
        if (args.length != index + 2 || !user && !args[0].equalsIgnoreCase("server")) {
            CustomTimerCommand.send(sender, "create-usage", "%alias%", alias);
            return true;
        }
        Player target = user ? Bukkit.getPlayerExact(args[1]) : null;
        if (user && (target == null || !target.isOnline())) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        String name = args[index];
        if (name.isBlank()) { CustomTimerCommand.send(sender, "invalid-name"); return true; }
        long seconds;
        try {
            seconds = Long.parseLong(args[index + 1]);
            if (seconds <= 0 || seconds > (Long.MAX_VALUE - System.currentTimeMillis()) / 1000L) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.invalid-time")));
            return true;
        }
        Map<String, ServerTimer> timers = user ? User.get(target).getCustomTimers() : Main.getInstance().getCustomServerTimers();
        if (timers.containsKey(name)) { CustomTimerCommand.send(sender, "already", "%name%", name); return true; }
        try {
            timers.put(name, new ServerTimer(name, seconds));
        } catch (ArithmeticException exception) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.invalid-time")));
            return true;
        }
        CustomTimerCommand.send(sender, "created", "%name%", name, "%target%", user ? target.getName() : "server", "%time%", C.formatChatTime(seconds));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.customtimer", "admin")) return List.of();
        if (args.length == 1) return List.of("server", "user").stream().filter(name -> name.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("user")) return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !(sender instanceof Player viewer) || viewer.canSee(p)).map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        return List.of();
    }
}
