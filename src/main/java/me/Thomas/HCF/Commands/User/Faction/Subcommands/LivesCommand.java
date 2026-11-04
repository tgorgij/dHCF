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
import java.util.Locale;
import java.util.logging.Level;

public class LivesCommand implements Subcommand {
    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override public String getName() { return "lives"; }
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
        if (args.length != 2 || !args[0].equalsIgnoreCase("add") && !args[0].equalsIgnoreCase("withdraw")) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.lives.usage")
                    .replace("%alias%", alias)));
            return true;
        }
        boolean withdraw = args[0].equalsIgnoreCase("withdraw");
        if (withdraw && faction.getMemberType(player.getUniqueId()).getValue() < Faction.MemberType.COLEADER.getValue()) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.coleader.not")));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        if (amount <= 0 || (long) (withdraw ? user.getLives() : faction.getLives()) + amount > Integer.MAX_VALUE) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        if (amount > (withdraw ? faction.getLives() : user.getLives())) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.lives." + (withdraw ? "insufficient-faction-lives" : "insufficient-lives"))));
            return true;
        }
        int personalLives = user.getLives(), factionLives = faction.getLives();
        user.setLives(personalLives + (withdraw ? amount : -amount));
        faction.setLives(factionLives + (withdraw ? -amount : amount));
        try {
            Main.getInstance().getDatabaseManager().save(List.of(user.snapshot(), faction.snapshot())).join();
        } catch (RuntimeException exception) {
            user.setLives(personalLives);
            faction.setLives(factionLives);
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not transfer faction lives", exception);
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.lives.failed")));
            return true;
        }
        sender.sendMessage(C.chat(localeConfig.getString("commands.faction.lives." + (withdraw ? "withdrawn" : "success"))
                .replace("%amount%", Integer.toString(amount))));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        User user = User.get(player);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction == null || !faction.hasMember(player.getUniqueId())) return List.of();
        return List.of("add", "withdraw").stream()
                .filter(action -> action.equals("add") || faction.getMemberType(player.getUniqueId()).getValue() >= Faction.MemberType.COLEADER.getValue())
                .filter(action -> action.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
