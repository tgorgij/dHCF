package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class LivesCommand implements Subcommand {
    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

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
        if (args.length != 2 || !args[0].equalsIgnoreCase("add")) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.lives.usage"))
                    .replace("%alias%", alias)));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        if (amount <= 0 || (long) faction.getLives() + amount > Integer.MAX_VALUE) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-amount")));
            return true;
        }
        if (amount > user.getLives()) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.lives.insufficient-lives")));
            return true;
        }
        user.setLives(user.getLives() - amount);
        faction.setLives(faction.getLives() + amount);
        Main.getInstance().saveTogether(user, faction);
        sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.lives.success"))
                .replace("%amount%", Integer.toString(amount))));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        User user = User.get(player);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction == null || !faction.hasMember(player.getUniqueId())) return List.of();
        return "add".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("add") : List.of();
    }
}
