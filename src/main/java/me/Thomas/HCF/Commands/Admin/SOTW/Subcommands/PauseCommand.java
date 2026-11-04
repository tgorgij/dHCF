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

public class PauseCommand implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "pause";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.sotw.pause", "admin")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.sotw.pause.usage")));
            return true;
        }
        ServerTimer timer = Main.getInstance().getServerTimer(ServerTimer.Type.SOTW);
        if (timer == null) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.sotw.not-running")));
            return true;
        }
        Main.getInstance().pauseServerTimer(ServerTimer.Type.SOTW);
        String state = timer.isPaused() ? "paused" : "resumed";
        sender.sendMessage(C.chat(localeConfig.getString("commands.sotw.pause." + state)));
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
