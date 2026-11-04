package me.Thomas.HCF.Commands.User.Economy;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BalanceCommand implements TabExecutor {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String s, @NotNull String @NotNull [] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
                return true;
            }
            User user = User.get(p);
            p.sendMessage(C.chat(localeConfig.getString("commands.balance.self").replace("%balance%", C.getFormattedMoney(user.getBalance()))));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.balance.usage").replace("%alias%", s)));
            return true;
        }
        User user = User.get(args[0]);
        if (user == null) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
            return true;
        }
        sender.sendMessage(C.chat(localeConfig.getString("commands.balance.other").replace("%player%", user.getName()).replace("%balance%", C.getFormattedMoney(user.getBalance()))));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command cmd,
            @NotNull String alias,
            @NotNull String @NotNull [] args
    ) {
        if (args.length == 1) {
            String input = args[0].toUpperCase(java.util.Locale.ROOT);

            return Main.getInstance().getAllUsers().values().stream()
                    .map(User::getName)
                    .filter(name -> name.toUpperCase(java.util.Locale.ROOT).startsWith(input))
                    .toList();
        }

        return List.of();
    }
}
