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

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class CaptainCommand implements Subcommand {
    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "captain";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    private Faction getFaction(Player player) {
        User user = User.get(player);
        return user != null && user.hasFaction() ? Faction.get(user.getFaction()) : null;
    }

    private boolean canManage(Player player, Faction faction) {
        if (faction == null || !faction.hasMember(player.getUniqueId())) return false;
        Faction.MemberType rank = faction.getMemberType(player.getUniqueId());
        return rank == Faction.MemberType.LEADER || rank == Faction.MemberType.COLEADER;
    }

    private void sendHelp(CommandSender sender, String alias) {
        for (String line : Main.getInstance().getMessagesConfig().getStringList("factions.help.captain")) {
            sender.sendMessage(C.chat(line.replace("%alias%", alias)));
        }
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
            return true;
        }
        Faction faction = getFaction(player);
        if (faction == null || !faction.hasMember(player.getUniqueId())) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.not-in")));
            return true;
        }
        if (!canManage(player, faction)) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.coleader.not")));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender, alias);
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        String path = "commands.faction.captain";
        if (action.equals("list") && args.length == 1) {
            List<String> names = faction.getMembers().entrySet().stream()
                    .filter(entry -> entry.getValue() == Faction.MemberType.CAPTAIN.getValue())
                    .map(entry -> User.get(entry.getKey())).filter(Objects::nonNull)
                    .map(User::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
            sender.sendMessage(C.chat(localeConfig.getString(path + ".list")
                    .replace("%players%", names.isEmpty() ? localeConfig.getString("primary.none")
                            : String.join(", ", names))));
            return true;
        }
        if ((!action.equals("add") && !action.equals("remove")) || args.length != 2) {
            sendHelp(sender, alias);
            return true;
        }
        User target = User.get(args[1]);
        if (target == null) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-player")));
            return true;
        }
        if (!Objects.equals(target.getFaction(), faction.getUUID()) || !faction.hasMember(target.getUUID())) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.not-member")));
            return true;
        }
        boolean add = action.equals("add");
        Faction.MemberType required = add ? Faction.MemberType.MEMBER : Faction.MemberType.CAPTAIN;
        if (faction.getMemberType(target.getUUID()) != required ||
                !faction.changeMemberRank(player.getUniqueId(), target.getUUID(), add)) {
            sender.sendMessage(C.chat(localeConfig.getString(path + "." + action + ".cannot")));
            return true;
        }
        faction.saveAsync();
        sender.sendMessage(C.chat(localeConfig.getString(path + "." + action + ".success")
                .replace("%player%", target.getName())));
        Player targetPlayer = Bukkit.getPlayer(target.getUUID());
        if (targetPlayer != null) {
            targetPlayer.sendMessage(C.chat(localeConfig.getString(path + "." + action + ".target")
                    .replace("%player%", player.getName())));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!(sender instanceof Player player)) return List.of();
        Faction faction = getFaction(player);
        if (!canManage(player, faction)) return List.of();
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return List.of("add", "remove", "list").stream().filter(value -> value.startsWith(input)).toList();
        }
        if (args.length != 2 || (!args[0].equalsIgnoreCase("add") && !args[0].equalsIgnoreCase("remove"))) {
            return List.of();
        }
        Faction.MemberType required = args[0].equalsIgnoreCase("add")
                ? Faction.MemberType.MEMBER : Faction.MemberType.CAPTAIN;
        String input = args[1].toUpperCase(Locale.ROOT);
        return faction.getMembers().entrySet().stream()
                .filter(entry -> entry.getValue() == required.getValue())
                .filter(entry -> !entry.getKey().equals(player.getUniqueId()) && !entry.getKey().equals(faction.getLeader()))
                .map(entry -> User.get(entry.getKey())).filter(Objects::nonNull).map(User::getName)
                .filter(value -> value.toUpperCase(Locale.ROOT).startsWith(input))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
