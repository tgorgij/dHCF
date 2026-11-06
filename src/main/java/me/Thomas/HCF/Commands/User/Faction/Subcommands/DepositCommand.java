package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class DepositCommand implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "deposit";
    }

    @Override
    public List<String> getAliases() {
        return List.of("d");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
            return true;
        }
        User user = User.get(p);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction == null || !faction.hasMember(p.getUniqueId())) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.not-in")));
            return true;
        }
        if (args.length != 1) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.deposit.usage").replace("%alias%", alias)));
            return true;
        }
        if (!Double.isFinite(user.getBalance()) || !Double.isFinite(faction.getBalance())
                || user.getBalance() < 0 || faction.getBalance() < 0) {
            p.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        BigDecimal amount;
        try {
            amount = (args[0].equalsIgnoreCase("all") ? BigDecimal.valueOf(user.getBalance())
                    : new BigDecimal(args[0])).stripTrailingZeros();
        } catch (NumberFormatException | ArithmeticException exception) {
            p.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        double minimum = Main.getInstance().getConfig().getDouble("economy.minimum-transfer", 1.0);
        if (amount.signum() <= 0 || amount.scale() > 2 || !Double.isFinite(minimum) || minimum < 0
                || amount.compareTo(BigDecimal.valueOf(minimum)) < 0) {
            p.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        BigDecimal balance = BigDecimal.valueOf(user.getBalance());
        if (amount.compareTo(balance) > 0) {
            p.sendMessage(C.chat(localeConfig.getString("primary.insufficient-funds")));
            return true;
        }
        BigDecimal remaining = balance.subtract(amount);
        BigDecimal received = BigDecimal.valueOf(faction.getBalance()).add(amount);
        double newBalance = remaining.doubleValue();
        double newTargetBalance = received.doubleValue();
        if (!Double.isFinite(newBalance) || !Double.isFinite(newTargetBalance)
                || BigDecimal.valueOf(newBalance).compareTo(remaining) != 0
                || BigDecimal.valueOf(newTargetBalance).compareTo(received) != 0) {
            p.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        user.setBalance(newBalance);
        faction.setBalance(newTargetBalance);
        Main.getInstance().saveTogether(user, faction);
        p.sendMessage(C.chat(localeConfig.getString("commands.faction.deposit.success").replace("%amount%", C.getFormattedMoney(amount.doubleValue()))));
        for (UUID uuid : faction.getOnlineMembers().keySet()) {
            Player player = Main.getInstance().getServer().getPlayer(uuid);
            if (player == null) continue;
            player.sendMessage(C.chat(localeConfig.getString("commands.faction.deposit.broadcast").replace("%player%", p.getName()).replace("%amount%", C.getFormattedMoney(amount.doubleValue()))));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (args.length == 1) {
            return List.of("all");
        }
        return List.of();
    }
}
