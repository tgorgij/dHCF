package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class TransferCommand implements Subcommand {
    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "transfer";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

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
        if (!player.getUniqueId().equals(faction.getLeader())) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.leader.not")));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.transfer.usage"))
                    .replace("%alias%", alias)));
            return true;
        }
        User target = User.get(args[0]);
        if (target == null) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-player")));
            return true;
        }
        if (!Objects.equals(target.getFaction(), faction.getUUID()) || !faction.hasMember(target.getUUID())) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.not-member")));
            return true;
        }
        if (player.getUniqueId().equals(target.getUUID())) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.transfer.self")));
            return true;
        }
        if (!faction.transferLeadership(player.getUniqueId(), target.getUUID())) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.leader.not")));
            return true;
        }
        faction.saveAsync();
        sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.transfer.success"))
                .replace("%player%", target.getName())));
        Player targetPlayer = Bukkit.getPlayer(target.getUUID());
        if (targetPlayer != null) {
            targetPlayer.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.transfer.target"))
                    .replace("%player%", player.getName())));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        User user = User.get(player);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction == null || !player.getUniqueId().equals(faction.getLeader())
                || !faction.hasMember(player.getUniqueId())) return List.of();
        String input = args[0].toUpperCase(Locale.ROOT);
        return faction.getMembers().keySet().stream()
                .filter(uuid -> !uuid.equals(player.getUniqueId()))
                .map(User::get).filter(Objects::nonNull).map(User::getName)
                .filter(name -> name.toUpperCase(Locale.ROOT).startsWith(input))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
