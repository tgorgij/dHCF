package me.Thomas.HCF.Commands.Staff.Chat.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Commands.Staff.Chat.ChatCommand;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

public class PauseCommand implements Subcommand {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

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
        if (!Permission.has(sender, "command.chat", "staff")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.chat.pause.usage"))
                    .replace("%alias%", alias)));
            return true;
        }
        boolean paused = ChatCommand.togglePaused();
        Bukkit.broadcast(C.chat(Objects.requireNonNull(localeConfig.getString("commands.chat.pause."
                + (paused ? "paused" : "resumed"))).replace("%player%", sender.getName())));
        return true;
    }
}
