package me.Thomas.HCF.Commands.Admin.SOTW.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class EnableCommand implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "enable";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
            return true;
        }
        if (args.length != 0) {
            player.sendMessage(C.chat(localeConfig.getString("commands.sotw.enable.usage")));
            return true;
        }
        if (!Main.getInstance().isSOTWOngoing()) {
            player.sendMessage(C.chat(localeConfig.getString("commands.sotw.not-running")));
            return true;
        }
        if (!Main.getInstance().enableSOTW(player.getUniqueId())) {
            player.sendMessage(C.chat(localeConfig.getString("commands.sotw.enable.already")));
            return true;
        }
        player.sendMessage(C.chat(localeConfig.getString("commands.sotw.enable.success")));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        return Subcommand.super.tabComplete(sender, args);
    }
}
