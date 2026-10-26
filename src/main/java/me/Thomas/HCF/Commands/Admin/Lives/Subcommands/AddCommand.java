package me.Thomas.HCF.Commands.Admin.Lives.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class AddCommand implements Subcommand {
    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();
    @Override public String getName() { return "add"; }
    @Override public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.lives", "admin")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.lives.add.usage"))
                    .replace("%alias%", alias)));
            return true;
        }
        User user = User.get(args[0]);
        if (user == null) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-player")));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        if (amount < 0) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        long result = (long) user.getLives() + amount;
        if (result < 0 || result > Integer.MAX_VALUE) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        user.setLives((int) result);
        user.saveAsync();
        sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.lives.add.success"))
                .replace("%player%", user.getName()).replace("%amount%", Integer.toString(amount))
                .replace("%lives%", Integer.toString(user.getLives()))));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!Permission.has(sender, "command.lives", "admin") || args.length != 1) return List.of();
        String input = args[0].toUpperCase(Locale.ROOT);
        return Main.getInstance().getAllUsers().values().stream().map(User::getName)
                .filter(name -> name.toUpperCase(Locale.ROOT).startsWith(input))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
