package me.Thomas.HCF.Commands.Staff;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class ReviveCommand implements TabExecutor {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Main.getInstance().isFeatureEnabled("deathbans")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.feature-disabled")));
            return true;
        }
        if (!Permission.has(sender, "command.revive", "staff")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.revive.usage", "&cUsage: /%alias% <player>")
                    .replace("%alias%", alias)));
            return true;
        }
        User user = User.get(args[0]);
        if (user == null) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-player")));
            return true;
        }
        if (user.getDeathbanExpiry() <= System.currentTimeMillis()) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.revive.not-deathbanned", "&c%player% is not deathbanned.")
                    .replace("%player%", user.getName())));
            return true;
        }
        user.setDeathbanExpiry(0L);
        user.saveAsync();
        sender.sendMessage(C.chat(localeConfig.getString("commands.revive.success", "&aYou revived %player%. They can now log back in.")
                .replace("%player%", user.getName())));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                               @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Main.getInstance().isFeatureEnabled("deathbans")) return List.of();
        if (!Permission.has(sender, "command.revive", "staff") || args.length != 1) return List.of();
        String input = args[0].toUpperCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        return Main.getInstance().getAllUsers().values().stream()
                .filter(user -> user.getDeathbanExpiry() > now)
                .map(User::getName)
                .filter(name -> name.toUpperCase(Locale.ROOT).startsWith(input))
                .toList();
    }
}
