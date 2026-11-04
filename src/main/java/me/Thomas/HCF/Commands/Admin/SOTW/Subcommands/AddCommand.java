package me.Thomas.HCF.Commands.Admin.SOTW.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Utils.Timer.ServerTimer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class AddCommand implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "add";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.sotw.add", "admin")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.sotw.add.usage")));
            return true;
        }
        ServerTimer timer = Main.getInstance().getServerTimer(ServerTimer.Type.SOTW);
        if (timer == null) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.sotw.not-running")));
            return true;
        }
        long time;
        try {
            time = Long.parseLong(args[0]);
            if (time == 0) {
                sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-time")));
                return true;
            }
            timer.addTime(time);
        } catch (NumberFormatException | ArithmeticException e) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-time")));
            return true;
        }
        String adjustment = Long.toString(Math.abs(time));
        String state = time > 0 ? "extended" : "shortened";
        sender.sendMessage(C.chat(localeConfig.getString("commands.sotw.add." + state)
                .replace("%time%", adjustment)));
        for (CommandSender player : Bukkit.getOnlinePlayers()) {
            Main.getInstance().sendMessage(player, "timers.sotw." + state);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        return Subcommand.super.tabComplete(sender, args);
    }
}
