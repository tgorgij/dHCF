package me.Thomas.HCF.Commands.User.Economy;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public class PayCommand implements TabExecutor {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.pay.usage")
                    .replace("%alias%", alias)));
            return true;
        }
        User target = User.get(args[0]);
        if (target == null) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-player")));
            return true;
        }
        if (target.getUUID().equals(player.getUniqueId())) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.pay.self")));
            return true;
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(args[1]).stripTrailingZeros();
        } catch (NumberFormatException | ArithmeticException exception) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.pay.invalid-amount")));
            return true;
        }
        double minimum = Main.getInstance().getConfig().getDouble("economy.minimum-transfer", 1.0);
        if (amount.signum() <= 0 || amount.scale() > 2
                || !Double.isFinite(minimum) || minimum < 0) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.pay.invalid-amount")));
            return true;
        }
        if (amount.compareTo(BigDecimal.valueOf(minimum)) < 0) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.pay.minimum")
                    .replace("%minimum%", C.getFormattedMoney(minimum))));
            return true;
        }
        User user = User.get(player);
        if (!Double.isFinite(user.getBalance()) || !Double.isFinite(target.getBalance())
                || user.getBalance() < 0 || target.getBalance() < 0) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        BigDecimal balance = BigDecimal.valueOf(user.getBalance());
        if (amount.compareTo(balance) > 0) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.insufficient-funds")));
            return true;
        }
        BigDecimal remaining = balance.subtract(amount);
        BigDecimal received = BigDecimal.valueOf(target.getBalance()).add(amount);
        double newBalance = remaining.doubleValue();
        double newTargetBalance = received.doubleValue();
        // Reject values the existing double balance fields cannot represent without losing money.
        if (!Double.isFinite(newBalance) || !Double.isFinite(newTargetBalance)
                || BigDecimal.valueOf(newBalance).compareTo(remaining) != 0
                || BigDecimal.valueOf(newTargetBalance).compareTo(received) != 0) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        user.setBalance(newBalance);
        target.setBalance(newTargetBalance);
        Main.getInstance().getDatabaseManager().save(List.of(user.snapshot(), target.snapshot()));
        String formattedAmount = C.getFormattedMoney(amount.doubleValue());
        player.sendMessage(C.chat(localeConfig.getString("commands.pay.success")
                .replace("%player%", target.getName()).replace("%amount%", formattedAmount)));
        Player recipient = Bukkit.getPlayer(target.getUUID());
        if (recipient != null) {
            recipient.sendMessage(C.chat(localeConfig.getString("commands.pay.received")
                    .replace("%player%", player.getName()).replace("%amount%", formattedAmount)));
        }
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        String input = args[0].toLowerCase(Locale.ROOT);
        return Main.getInstance().getAllUsers().values().stream()
                .filter(user -> !user.getUUID().equals(player.getUniqueId()))
                .map(User::getName)
                .filter(name -> !name.isEmpty() && name.toLowerCase(Locale.ROOT).startsWith(input))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
