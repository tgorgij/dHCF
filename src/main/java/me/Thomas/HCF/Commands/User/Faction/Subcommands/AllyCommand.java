package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

public class AllyCommand implements Subcommand {
    @Override
    public String getName() { return "ally"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    public static void broadcast(Faction faction, Component message) {
        for (UUID uuid : faction.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) member.sendMessage(message);
        }
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        if (args.length != 1) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.ally.usage").replace("%alias%", alias)));
            return true;
        }
        User user = User.get(p);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction == null || !faction.hasMember(p.getUniqueId()) || faction.getType() != Faction.Type.PLAYER) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.not-in")));
            return true;
        }
        if (faction.getMemberType(p.getUniqueId()).getValue() < Faction.MemberType.COLEADER.getValue()) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.coleader.not")));
            return true;
        }
        Faction target = Faction.get(args[0]);
        if (target == null || target.getType() != Faction.Type.PLAYER) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.ally.invalid-faction")));
            return true;
        }
        if (target.getUUID().equals(faction.getUUID())) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.ally.self")));
            return true;
        }
        if (faction.isAllied(target.getUUID())) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.ally.already")));
            return true;
        }
        int maximum = Math.max(0, Main.getInstance().getConfig().getInt("factions.allies.max", 1));
        if (faction.getAllies().size() >= maximum || target.getAllies().size() >= maximum) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.ally.limit").replace("%max%", Integer.toString(maximum))));
            return true;
        }
        if (faction.hasAllyRequest(target.getUUID())) {
            try { faction.changeAlliance(target, true); }
            catch (RuntimeException exception) {
                Main.getInstance().getLogger().log(Level.SEVERE, "Could not accept faction alliance", exception);
                p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.ally.failed")));
                return true;
            }
            Component message = C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.ally.accepted")
                    .replace("%faction%", faction.getName()).replace("%ally%", target.getName()));
            broadcast(faction, message);
            broadcast(target, message);
        } else if (target.hasAllyRequest(faction.getUUID())) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.ally.pending")));
        } else {
            target.requestAlly(faction.getUUID());
            broadcast(faction, C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.ally.requested")
                    .replace("%player%", p.getName()).replace("%faction%", target.getName())));
            broadcast(target, C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.ally.request-received")
                    .replace("%faction%", faction.getName())));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player p) || args.length != 1) return List.of();
        User user = User.get(p);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction == null || !faction.hasMember(p.getUniqueId())
                || faction.getMemberType(p.getUniqueId()).getValue() < Faction.MemberType.COLEADER.getValue()) return List.of();
        return Main.getInstance().getAllFactions().values().stream().filter(target -> target.getType() == Faction.Type.PLAYER
                        && !target.getUUID().equals(faction.getUUID()) && !faction.isAllied(target.getUUID()))
                .map(Faction::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
