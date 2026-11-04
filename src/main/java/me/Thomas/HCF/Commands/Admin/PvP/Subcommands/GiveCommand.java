package me.Thomas.HCF.Commands.Admin.PvP.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;

public class GiveCommand implements Subcommand {
    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();
    @Override
    public String getName() {
        return "give";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.pvp", "admin")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.pvp.give.usage")
                    .replace("%alias%", alias)));
            return true;
        }
        User user = User.get(args[0]);
        if (user == null) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-player")));
            return true;
        }
        if (!user.givePvpTimer()) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.pvp.give.disabled")));
            return true;
        }
        user.saveAsync();
        sender.sendMessage(C.chat(localeConfig.getString("commands.pvp.give.success")
                .replace("%player%", user.getName())
                .replace("%time%", C.formatChatTime(Timer.Type.PVP.getLength()))));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!Permission.has(sender, "command.pvp", "admin") || args.length != 1) return List.of();
        String input = args[0].toLowerCase(Locale.ROOT);
        return Main.getInstance().getAllUsers().values().stream().map(User::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
