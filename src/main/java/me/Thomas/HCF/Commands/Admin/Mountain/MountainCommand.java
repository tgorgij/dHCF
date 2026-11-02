package me.Thomas.HCF.Commands.Admin.Mountain;

import me.Thomas.HCF.Commands.Admin.Mountain.Subcommands.*;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Mountain.Mountain;
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

public class MountainCommand implements TabExecutor {

    private final Map<String, Subcommand> subcommands = new HashMap<>();

    public MountainCommand() {
        register(new CreateCommand());
        register(new DeleteCommand());
        register(new ResetCommand());
        register(new SetIntervalCommand());
        register(new SetBlockCommand());
        register(new SetPos1Command());
        register(new SetPos2Command());
    }

    private void register(Subcommand subcommand) {
        subcommands.put(subcommand.getName().toLowerCase(Locale.ROOT), subcommand);
        for (String alias : subcommand.getAliases()) subcommands.put(alias.toLowerCase(Locale.ROOT), subcommand);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.mountain", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        Subcommand subcommand = args.length < 2 ? null : subcommands.get(args[1].toLowerCase(Locale.ROOT));
        if (subcommand == null) {
            for (String line : Main.getInstance().getMessagesConfig().getStringList("help.mountain")) {
                sender.sendMessage(C.chat(line.replace("%alias%", alias).replace("%mountain%", args.length == 0 ? "<name>" : args[0])));
            }
            return true;
        }
        if (!subcommand.getName().equals("create") && Mountain.get(args[0]) == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.mountain.not-found")
                    .replace("%mountain%", args[0])));
            return true;
        }
        String[] subcommandArgs = new String[args.length - 1];
        subcommandArgs[0] = args[0];
        System.arraycopy(args, 2, subcommandArgs, 1, args.length - 2);
        try {
            return subcommand.execute(sender, subcommandArgs, alias);
        } catch (IllegalArgumentException exception) {
            send(sender, exception.getMessage());
            return true;
        } catch (RuntimeException exception) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not execute Mountain command", exception);
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.mountain.failed")));
            return true;
        }
    }

    public static void send(CommandSender sender, String key, String... replacements) {
        String message = Main.getInstance().getLocaleConfig().getString("commands.mountain." + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) message = message.replace(replacements[i], replacements[i + 1]);
        sender.sendMessage(C.chat(message));
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.mountain", "admin") || args.length == 0) return List.of();
        String input = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return Main.getInstance().getAllMountains().values().stream().map(Mountain::getName)
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
