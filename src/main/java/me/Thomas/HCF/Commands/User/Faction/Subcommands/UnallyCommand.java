package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class UnallyCommand implements Subcommand {
    @Override
    public String getName() { return "unally"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        if (args.length != 1) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.unally.usage").replace("%alias%", alias)));
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
        if (target == null || !faction.isAllied(target.getUUID())) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.unally.not-allied")));
            return true;
        }
        try { faction.changeAlliance(target, false); }
        catch (RuntimeException exception) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not remove faction alliance", exception);
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.ally.failed")));
            return true;
        }
        Component message = C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.unally.success")
                .replace("%player%", p.getName()).replace("%faction%", faction.getName()).replace("%ally%", target.getName()));
        AllyCommand.broadcast(faction, message);
        AllyCommand.broadcast(target, message);
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player p) || args.length != 1) return List.of();
        User user = User.get(p);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction == null || !faction.hasMember(p.getUniqueId())
                || faction.getMemberType(p.getUniqueId()).getValue() < Faction.MemberType.COLEADER.getValue()) return List.of();
        return faction.getAllies().stream().map(Faction::get).map(Faction::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
