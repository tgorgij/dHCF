package me.Thomas.HCF.Commands.Admin.SOTW.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Utils.Timer.ServerTimer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

public class StartCommand implements Subcommand {

    private final FileConfiguration messagesConfig = Main.getInstance().getMessagesConfig();
    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

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
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("primary.no-permission"))));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.sotw.start.usage"))));
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
        sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.sotw.start.success"))));
        for (CommandSender player : Bukkit.getOnlinePlayers()) {
            sendMessage(player, "timers.sotw.start");
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        return Subcommand.super.tabComplete(sender, args);
    }

    private void sendMessage(CommandSender sender, String message) {
        for (String s : messagesConfig.getStringList(message)) {
            sender.sendMessage(C.chat(s));
        }
    }
}
