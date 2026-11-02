package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class ForceJoinCommand implements Subcommand {

    @Override public String getName() { return "forcejoin"; }
    @Override public List<String> getAliases() { return List.of(); }

    private void send(CommandSender sender, String key, String... replacements) {
        String message = Main.getInstance().getLocaleConfig().getString("commands.faction." + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) message = message.replace(replacements[i], replacements[i + 1]);
        sender.sendMessage(C.chat(message));
    }

    private Faction find(CommandSender sender, String name) {
        Faction faction = Faction.get(name);
        if (faction == null) sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-faction")));
        else if (faction.getType() != Faction.Type.PLAYER) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.only-player-faction")));
            return null;
        }
        return faction;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.faction." + getName(), "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) { send(sender, getName() + ".usage", "%alias%", alias); return true; }
        try {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
                return true;
            }
            User user = User.get(p);
            if (user.hasFaction()) { send(sender, "join.already-in"); return true; }
            Faction faction = find(sender, args[0]);
            if (faction == null) return true;
            faction.changeMembership(user, () -> {
                if (!faction.hasMember(user.getUUID())) faction.addMember(user.getUUID(), Faction.MemberType.MEMBER.getValue());
                user.setFaction(faction.getUUID());
            });
            if (Main.getInstance().hasInvite(faction.getUUID(), user.getUUID())) Main.getInstance().removeInvite(faction.getUUID(), user.getUUID());
            send(sender, getName() + ".success", "%faction%", faction.getName());
        } catch (IllegalArgumentException exception) { send(sender, exception.getMessage()); }
        catch (RuntimeException exception) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not execute faction " + getName(), exception);
            send(sender, "admin.failed");
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.faction." + getName(), "admin")) return List.of();
        if (args.length != 1) return List.of();
        String input = args[0].toLowerCase(Locale.ROOT);
        return Main.getInstance().getAllFactions().values().stream().filter(faction -> faction.getType() == Faction.Type.PLAYER)
                .map(Faction::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
