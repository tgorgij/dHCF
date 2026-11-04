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
        if (!user.hasFaction()) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.not-in")));
            return true;
        }
        Faction faction = Faction.get(user.getFaction());
        if (args.length != 1) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.deposit.usage").replace("%alias%", alias)));
            return true;
        }
        double amount;
        if (args[0].equalsIgnoreCase("all")) {
            amount = user.getBalance();
        } else {
            try {
                amount = Double.parseDouble(args[0]);
            } catch (NumberFormatException _) {
                p.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
                return true;
            }
        }
        if (!Double.isFinite(amount) || amount <= 0
                || amount < Main.getInstance().getConfig().getDouble("economy.minimum-transfer")
                || !Double.isFinite(faction.getBalance() + amount)) {
            p.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        if (amount > user.getBalance()) {
            p.sendMessage(C.chat(localeConfig.getString("primary.insufficient-funds")));
            return true;
        }
        user.setBalance(user.getBalance() - amount);
        faction.setBalance(faction.getBalance() + amount);
        Main.getInstance().saveTogether(user, faction);
        p.sendMessage(C.chat(localeConfig.getString("commands.faction.deposit.success").replace("%amount%", C.getFormattedMoney(amount))));
        for (UUID uuid : faction.getOnlineMembers().keySet()) {
            Player player = Main.getInstance().getServer().getPlayer(uuid);
            if (player == null) continue;
            player.sendMessage(C.chat(localeConfig.getString("commands.faction.deposit.broadcast").replace("%player%", p.getName()).replace("%amount%", C.getFormattedMoney(amount))));
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
