package me.Thomas.HCF.Commands.Admin.SOTW.Subcommands;

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
import java.util.Objects;

public class CancelCommand implements Subcommand {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "cancel";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.sotw.cancel", "admin")) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("primary.no-permission"))));
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.sotw.cancel.usage"))));
            return true;
        }
        if (!Main.getInstance().hasServerTimer(ServerTimer.Type.SOTW)) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.sotw.not-running")));
            return true;
        }
        Main.getInstance().removeServerTimer(ServerTimer.Type.SOTW);
        sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.sotw.cancel.success"))));
        for (CommandSender player : Bukkit.getOnlinePlayers()) {
            Main.getInstance().sendMessage(player, "timers.sotw.cancel");
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        return Subcommand.super.tabComplete(sender, args);
    }
}
