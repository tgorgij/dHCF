package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Names.Names;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class ForceRenameCommand implements Subcommand {

    @Override public String getName() { return "forcerename"; }
    @Override public List<String> getAliases() { return List.of(); }

    private void send(CommandSender sender, String key, String... replacements) {
        String message = Main.getInstance().getLocaleConfig().getString("commands.faction." + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) message = message.replace(replacements[i], replacements[i + 1]);
        sender.sendMessage(C.chat(message));
    }

    private Faction find(CommandSender sender, String name) {
        Faction faction = Faction.get(name);
        if (faction == null) sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-faction")));
        return faction;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.faction." + getName(), "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 2) { send(sender, getName() + ".usage", "%alias%", alias); return true; }
        try {
            Faction faction = find(sender, args[0]);
            if (faction == null || Names.isInvalid(args[1], sender)) return true;
            Faction existing = Faction.get(args[1]);
            if (existing != null && !existing.getUUID().equals(faction.getUUID())) { send(sender, "already-exists"); return true; }
            String previous = faction.getName();
            Main.getInstance().renameFaction(faction.getUUID(), args[1]);
            faction.setName(args[1]);
            try { faction.save(); }
            catch (RuntimeException exception) {
                Main.getInstance().renameFaction(faction.getUUID(), previous);
                faction.setName(previous);
                throw exception;
            }
            send(sender, getName() + ".success", "%faction%", previous, "%name%", faction.getName());
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
        return Main.getInstance().getAllFactions().values().stream()
                .map(Faction::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
