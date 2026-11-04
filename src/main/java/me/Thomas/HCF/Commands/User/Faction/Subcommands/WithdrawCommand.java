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
        double amount;
        try {
            amount = args[0].equalsIgnoreCase("all") ? faction.getBalance() : Double.parseDouble(args[0]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        if (!Double.isFinite(amount) || amount <= 0
                || amount < Main.getInstance().getConfig().getDouble("economy.minimum-transfer")
                || !Double.isFinite(user.getBalance() + amount)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        if (amount > faction.getBalance()) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.withdraw.insufficient-funds")));
            return true;
        }
        faction.setBalance(faction.getBalance() - amount);
        user.setBalance(user.getBalance() + amount);
        Main.getInstance().saveTogether(user, faction);
        sender.sendMessage(C.chat(localeConfig.getString("commands.faction.withdraw.success").replace("%amount%", C.getFormattedMoney(amount))));
        for (UUID uuid : faction.getOnlineMembers().keySet()) {
            if (uuid.equals(player.getUniqueId())) continue;
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) member.sendMessage(C.chat(localeConfig.getString("commands.faction.withdraw.broadcast")
                    .replace("%player%", player.getName()).replace("%amount%", C.getFormattedMoney(amount))));
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
