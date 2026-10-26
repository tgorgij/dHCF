package me.Thomas.HCF.Commands.Admin.Economy.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public class TakeCommand implements Subcommand {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "take";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.economy.take", "admin")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.economy.take.usage"))
                    .replace("%alias%", alias)));
            return true;
        }
        User user = User.get(args[0]);
        if (user == null) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-player")));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        if (!Double.isFinite(amount) || amount < 0) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        double balance = user.getBalance();
        if (!Double.isFinite(balance) || balance < 0) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        if (amount > balance) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.economy.insufficient-funds")));
            return true;
        }
        double result = BigDecimal.valueOf(balance).subtract(BigDecimal.valueOf(amount)).doubleValue();
        if (!Double.isFinite(result)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        user.setBalance(result);
        user.saveAsync();
        sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.economy.take.success")).replace("%player%", user.getName()).replace("%amount%", C.getFormattedMoney(amount))));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!Permission.has(sender, "command.economy.take", "admin")) return List.of();
        if (args.length == 1) {
            String input = args[0].toUpperCase(java.util.Locale.ROOT);
            return Main.getInstance().getAllUsers().values().stream()
                    .map(User::getName)
                    .filter(name -> name.toUpperCase(java.util.Locale.ROOT).startsWith(input))
                    .toList();
        }
        if (args.length == 2) {
            return List.of("100", "500", "1000", "5000", "10000");
        }
        return List.of();
    }
}
