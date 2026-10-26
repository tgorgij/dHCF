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

public final class PromoteCommand implements Subcommand {
    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "promote";
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
        if (!user.hasFaction()) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.not-in")));
            return true;
        }
        Faction faction = Faction.get(user.getFaction());
        Faction.MemberType actor = faction.getMemberType(player.getUniqueId());
        if (!faction.hasMember(player.getUniqueId()) ||
                (actor != Faction.MemberType.LEADER && actor != Faction.MemberType.COLEADER)) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.coleader.not")));
            return true;
        }
        String path = "commands.faction." + getName();
        if (args.length != 1) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString(path + ".usage"))
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
        if (!faction.changeMemberRank(player.getUniqueId(), target.getUUID(), true)) {
            sender.sendMessage(C.chat(localeConfig.getString(path + ".cannot")));
            return true;
        }
        faction.saveAsync();
        String rank = localeConfig.getString("commands.faction.promote.names."
                + faction.getMemberType(target.getUUID()).name().toLowerCase(Locale.ROOT));
        sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString(path + ".success"))
                .replace("%player%", target.getName()).replace("%rank%", Objects.requireNonNull(rank))));
        Player targetPlayer = Bukkit.getPlayer(target.getUUID());
        if (targetPlayer != null) {
            targetPlayer.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString(path + ".target"))
                    .replace("%player%", player.getName()).replace("%rank%", rank)));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        User user = User.get(player);
        if (!user.hasFaction()) return List.of();
        Faction faction = Faction.get(user.getFaction());
        if (faction == null || !faction.hasMember(player.getUniqueId())) return List.of();
        Faction.MemberType actor = faction.getMemberType(player.getUniqueId());
        String input = args[0].toUpperCase(Locale.ROOT);
        return faction.getMembers().keySet().stream()
                .filter(uuid -> !uuid.equals(player.getUniqueId()) && !uuid.equals(faction.getLeader()))
                .filter(uuid -> Faction.MemberType.getChangedRank(actor, faction.getMemberType(uuid), true) != null)
                .map(User::get).filter(Objects::nonNull).map(User::getName)
                .filter(name -> name.toUpperCase(Locale.ROOT).startsWith(input))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
