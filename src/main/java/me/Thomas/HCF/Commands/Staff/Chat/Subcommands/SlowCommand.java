package me.Thomas.HCF.Commands.Staff.Chat.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Commands.Staff.Chat.ChatCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class SlowCommand implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "slow";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.chat", "staff")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.chat.slow.usage")
                    .replace("%alias%", alias)));
            return true;
        }
        int seconds;
        try {
            seconds = Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.chat.slow.invalid-time")));
            return true;
        }
        if (seconds < 0) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.chat.slow.invalid-time")));
            return true;
        }
        ChatCommand.setSlowSeconds(seconds);
        Bukkit.broadcast(C.chat(localeConfig.getString("commands.chat.slow."
                + (seconds == 0 ? "disabled" : "enabled"))
                .replace("%player%", sender.getName()).replace("%time%", Integer.toString(seconds))));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!Permission.has(sender, "command.chat", "staff") || args.length != 1) return List.of();
        return List.of("0", "3", "5", "10", "30", "60").stream()
                .filter(value -> value.startsWith(args[0])).toList();
    }
}
