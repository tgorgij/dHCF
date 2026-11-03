package me.Thomas.HCF.Commands.Admin.KOTH;

import me.Thomas.HCF.Commands.Admin.KOTH.Subcommands.*;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.KOTH.KOTH;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public class KOTHCommand implements TabExecutor {

    private final Map<String, Subcommand> subcommands = new HashMap<>();

    public KOTHCommand() {
        register(new CreateCommand());
        register(new DeleteCommand());
        register(new StartCommand());
        register(new CancelCommand());
        register(new ExtendCommand());
        register(new SetColorCommand());
        register(new SetPearlableCommand());
        register(new SetFactionCommand());
        register(new SetPos1Command());
        register(new SetPos2Command());
        register(new RenameCommand());
    }

    private void register(Subcommand subcommand) {
        subcommands.put(subcommand.getName().toLowerCase(Locale.ROOT), subcommand);
        for (String alias : subcommand.getAliases()) subcommands.put(alias.toLowerCase(Locale.ROOT), subcommand);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (!Main.getInstance().isFeatureEnabled("koth")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.feature-disabled")));
            return true;
        }
        if (!Permission.has(sender, "command.koth", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        Subcommand subcommand = args.length < 2 ? null : subcommands.get(args[1].toLowerCase(Locale.ROOT));
        if (subcommand == null) {
            for (String line : Main.getInstance().getMessagesConfig().getStringList("help.koth")) {
                sender.sendMessage(C.chat(line.replace("%alias%", alias).replace("%koth%", args.length == 0 ? "<name>" : args[0])));
            }
            return true;
        }
        if (!subcommand.getName().equals("create") && KOTH.get(args[0]) == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.koth.not-found")
                    .replace("%koth%", args[0])));
            return true;
        }
        String[] subcommandArgs = new String[args.length - 1];
        subcommandArgs[0] = args[0];
        System.arraycopy(args, 2, subcommandArgs, 1, args.length - 2);
        try {
            return subcommand.execute(sender, subcommandArgs, alias);
        } catch (RuntimeException exception) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not execute KOTH command", exception);
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.koth.failed")));
            return true;
        }
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Main.getInstance().isFeatureEnabled("koth")) return List.of();
        if (!Permission.has(sender, "command.koth", "admin") || args.length == 0) return List.of();
        String input = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return Main.getInstance().getAllKOTHs().values().stream().map(KOTH::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        if (args.length == 2) return subcommands.keySet().stream().filter(name -> name.startsWith(input)).sorted().toList();
        Subcommand subcommand = subcommands.get(args[1].toLowerCase(Locale.ROOT));
        if (subcommand == null) return List.of();
        String[] subcommandArgs = new String[args.length - 1];
        subcommandArgs[0] = args[0];
        System.arraycopy(args, 2, subcommandArgs, 1, args.length - 2);
        return subcommand.tabComplete(sender, subcommandArgs);
    }
}
