package me.Thomas.HCF.Commands.Admin.CustomTimer.Subcommands;

import me.Thomas.HCF.Commands.Admin.CustomTimer.CustomTimerCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Utils.Timer.ServerTimer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CancelCommand implements Subcommand {
    @Override
    public String getName() { return "cancel"; }
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
        if (args.length != index + 1 || !user && !args[0].equalsIgnoreCase("server")) {
            CustomTimerCommand.send(sender, "cancel-usage", "%alias%", alias);
            return true;
        }
        User target = user ? User.get(args[1]) : null;
        if (user && target == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        String name = args[index];
        Map<String, ServerTimer> timers = user ? target.getCustomTimers() : Main.getInstance().getCustomServerTimers();
        if (timers.remove(name) == null) { CustomTimerCommand.send(sender, "not-found", "%name%", name); return true; }
        CustomTimerCommand.send(sender, "cancelled", "%name%", name, "%target%", user ? target.getName() : "server");
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.customtimer", "admin")) return List.of();
        if (args.length == 1) return List.of("server", "user").stream().filter(name -> name.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        boolean user = args.length > 0 && args[0].equalsIgnoreCase("user");
        if (args.length == 2 && user) return Main.getInstance().getAllUsers().values().stream()
                .filter(target -> !target.getCustomTimers().isEmpty()).map(User::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        int index = user ? 2 : 1;
        if (args.length != index + 1 || !user && !args[0].equalsIgnoreCase("server")) return List.of();
        User target = user ? User.get(args[1]) : null;
        if (user && target == null) return List.of();
        return (user ? target.getCustomTimers() : Main.getInstance().getCustomServerTimers()).keySet().stream()
                .filter(name -> name.startsWith(args[index])).sorted().toList();
    }
}
