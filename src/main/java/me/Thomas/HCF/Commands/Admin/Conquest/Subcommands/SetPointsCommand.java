package me.Thomas.HCF.Commands.Admin.Conquest.Subcommands;

import me.Thomas.HCF.Commands.Admin.Conquest.ConquestCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Conquest.Conquest;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class SetPointsCommand implements Subcommand {
    @Override public String getName() { return "setpoints"; }
    @Override public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.conquest", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 2) { ConquestCommand.send(sender, "setpoints-usage", "%alias%", alias); return true; }
        Faction faction = Faction.get(args[0]);
        if (faction == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-faction")));
            return true;
        }
        int points;
        try { points = Integer.parseInt(args[1]); }
        catch (NumberFormatException exception) { ConquestCommand.send(sender, "invalid-points"); return true; }
        Conquest.get().setPoints(faction.getUUID(), points);
        ConquestCommand.send(sender, "points-set", "%faction%", faction.getName(), "%points%", Integer.toString(points));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.conquest", "admin") || args.length != 1) return List.of();
        String input = args[0].toLowerCase(Locale.ROOT);
        return Main.getInstance().getAllFactions().values().stream().filter(faction -> faction.getType() == Faction.Type.PLAYER)
                .map(Faction::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
