package me.Thomas.HCF.Commands.User.Faction.Subcommands;

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

import java.util.*;

public class JoinCommand implements FactionSubcommand{

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "join";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
            return true;
        }
        if (args.length != 1) {
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.join.usage")).replace("%alias%", alias)));
            return true;
        }
        User user = User.get(p);
        String name = args[0];
        Faction faction = Main.getInstance().getFaction(name);
        if (faction == null) {
            p.sendMessage(C.chat(localeConfig.getString("primary.no-faction")));
            return true;
        }
        if (!faction.getType().equals(Faction.Type.PLAYER)) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.join.system")));
            return true;
        }
        if (user.hasFaction()) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.join.already-in")));
            return true;
        }
        if (faction.isRaidable()) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.join.raidable")));
            return true;
        }
        if (faction.getMembers().size() >= Main.getInstance().getConfig().getInt("factions.members.max")) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.join.max")));
            return true;
        }
        if (!faction.isOpen() && !Main.getInstance().hasInvite(faction.getUUID(), p.getUniqueId())) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.join.not-invited")));
            return true;
        }
        p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.join.success")).replace("%faction%", faction.getName())));
        for (UUID uuid : faction.getOnlineMembers().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            player.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.join.broadcast")).replace("%player%", p.getName())));
        }
        faction.addMember(p.getUniqueId(), 0);
        user.setFaction(faction.getUUID());

        if (Main.getInstance().hasInvite(faction.getUUID(), p.getUniqueId())) {
            Main.getInstance().removeInvite(faction.getUUID(), p.getUniqueId());
        }

        user.saveAsync();
        faction.saveAsync();
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!(sender instanceof Player p)) return List.of();
        if (args.length != 1) return List.of();
        ArrayList<String> list = new ArrayList<>();
        for (UUID uuid : Main.getInstance().getInvitesForUser(p.getUniqueId())) {
            Faction faction = Main.getInstance().getFaction(uuid);
            if (faction == null) {
                Main.getInstance().removeInvite(uuid, p.getUniqueId());
                continue;
            }
            list.add(faction.getName());
        }
        String input = args[0].toUpperCase(Locale.ROOT);
        return list.stream()
                .filter(Objects::nonNull)
                .filter(name -> name.toUpperCase(Locale.ROOT).startsWith(input))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }
}
