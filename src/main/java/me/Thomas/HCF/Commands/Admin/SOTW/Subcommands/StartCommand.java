package me.Thomas.HCF.Commands.Admin.SOTW.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Utils.Timer.ServerTimer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class StartCommand implements Subcommand {

    private final FileConfiguration messagesConfig = Main.getInstance().getMessagesConfig();
    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "start";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.sotw.start", "admin")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.sotw.start.usage")));
            return true;
        }
        if (Main.getInstance().hasServerTimer(ServerTimer.Type.SOTW)) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.sotw.running")));
            return true;
        }
        long time;
        try {
            time = Long.parseLong(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-time")));
            return true;
        }
        ServerTimer timer = new ServerTimer(ServerTimer.Type.SOTW, time);
        Main.getInstance().addServerTimer(timer);
        sender.sendMessage(C.chat(localeConfig.getString("commands.sotw.start.success")));
        for (CommandSender player : Bukkit.getOnlinePlayers()) {
            Main.getInstance().sendMessage(player, "timers.sotw.start");
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        return Subcommand.super.tabComplete(sender, args);
    }
}
