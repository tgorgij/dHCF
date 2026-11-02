package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class ForceKickCommand implements Subcommand {

    @Override public String getName() { return "forcekick"; }
    @Override public List<String> getAliases() { return List.of(); }

    private void send(CommandSender sender, String key, String... replacements) {
        String message = Main.getInstance().getLocaleConfig().getString("commands.faction." + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) message = message.replace(replacements[i], replacements[i + 1]);
        sender.sendMessage(C.chat(message));
    }

    private User target(CommandSender sender, String name) {
        User user = User.get(name);
        if (user == null) sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
        return user;
    }

    private Faction membership(User user) {
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        return faction != null && faction.getType() == Faction.Type.PLAYER && faction.hasMember(user.getUUID()) ? faction : null;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.faction." + getName(), "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) { send(sender, getName() + ".usage", "%alias%", alias); return true; }
        try {
            User user = target(sender, args[0]);
            if (user == null) return true;
            Faction faction = membership(user);
            if (faction == null) { send(sender, "admin.no-membership"); return true; }
            if (user.getUUID().equals(faction.getLeader())) { send(sender, "admin.transfer-first"); return true; }
            faction.changeMembership(user, () -> {
                faction.removeMember(user.getUUID());
                user.setFaction(null);
                user.removeTimer(Timer.Type.HOME);
            });
            send(sender, getName() + ".success", "%faction%", faction.getName(), "%player%", user.getName());
            Player online = Bukkit.getPlayer(user.getUUID());
            if (online != null && online != sender) send(online, "admin.target", "%admin%", sender.getName(), "%faction%", faction.getName(), "%action%", getName());
        } catch (IllegalArgumentException exception) { send(sender, exception.getMessage()); }
        catch (RuntimeException exception) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not execute faction " + getName(), exception);
            send(sender, "admin.failed");
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.faction." + getName(), "admin") || args.length != 1) return List.of();
        String input = args[0].toLowerCase(Locale.ROOT);
        return Main.getInstance().getAllUsers().values().stream().filter(user -> membership(user) != null)
                .map(User::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
