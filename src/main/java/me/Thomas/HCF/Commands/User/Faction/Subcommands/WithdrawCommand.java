package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import java.math.BigDecimal;
import java.util.*;

public final class WithdrawCommand implements Subcommand {
    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();
    @Override public String getName() { return "withdraw"; }
    @Override public List<String> getAliases() { return List.of(); }
    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
            return true;
        }
        User user = User.get(player);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction == null || !faction.hasMember(player.getUniqueId())) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.not-in")));
            return true;
        }
        if (faction.getMemberType(player.getUniqueId()).getValue() < Faction.MemberType.CAPTAIN.getValue()) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.captain.not")));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.withdraw.usage").replace("%alias%", alias)));
            return true;
        }
        if (!Double.isFinite(user.getBalance()) || !Double.isFinite(faction.getBalance())
                || user.getBalance() < 0 || faction.getBalance() < 0) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        BigDecimal amount;
        try {
            amount = (args[0].equalsIgnoreCase("all") ? BigDecimal.valueOf(faction.getBalance())
                    : new BigDecimal(args[0])).stripTrailingZeros();
        } catch (NumberFormatException | ArithmeticException exception) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        double minimum = Main.getInstance().getConfig().getDouble("economy.minimum-transfer", 1.0);
        if (amount.signum() <= 0 || amount.scale() > 2 || !Double.isFinite(minimum) || minimum < 0
                || amount.compareTo(BigDecimal.valueOf(minimum)) < 0) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        BigDecimal balance = BigDecimal.valueOf(faction.getBalance());
        if (amount.compareTo(balance) > 0) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.withdraw.insufficient-funds")));
            return true;
        }
        BigDecimal remaining = balance.subtract(amount);
        BigDecimal received = BigDecimal.valueOf(user.getBalance()).add(amount);
        double newBalance = remaining.doubleValue();
        double newTargetBalance = received.doubleValue();
        if (!Double.isFinite(newBalance) || !Double.isFinite(newTargetBalance)
                || BigDecimal.valueOf(newBalance).compareTo(remaining) != 0
                || BigDecimal.valueOf(newTargetBalance).compareTo(received) != 0) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        faction.setBalance(newBalance);
        user.setBalance(newTargetBalance);
        Main.getInstance().saveTogether(user, faction);
        sender.sendMessage(C.chat(localeConfig.getString("commands.faction.withdraw.success").replace("%amount%", C.getFormattedMoney(amount.doubleValue()))));
        for (UUID uuid : faction.getOnlineMembers().keySet()) {
            if (uuid.equals(player.getUniqueId())) continue;
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) member.sendMessage(C.chat(localeConfig.getString("commands.faction.withdraw.broadcast")
                    .replace("%player%", player.getName()).replace("%amount%", C.getFormattedMoney(amount.doubleValue()))));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        User user = User.get(player);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction == null || !faction.hasMember(player.getUniqueId())
                || faction.getMemberType(player.getUniqueId()).getValue() < Faction.MemberType.CAPTAIN.getValue()) return List.of();
        return "all".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("all") : List.of();
    }
}
