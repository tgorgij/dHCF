package me.Thomas.HCF.Commands.Admin.DTC;

import me.Thomas.HCF.Commands.Admin.DTC.Subcommands.*;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public class DTCCommand implements TabExecutor {
    private final Map<String, Subcommand> subcommands = new HashMap<>();

    public DTCCommand() {
        register(new StartCommand());
        register(new CancelCommand());
        register(new SetCoreCommand());
        register(new ClearCoreCommand());
        register(new SetHealthCommand());
    }
    private void register(Subcommand subcommand) {
        subcommands.put(subcommand.getName().toLowerCase(Locale.ROOT), subcommand);
        for (String alias : subcommand.getAliases()) subcommands.put(alias.toLowerCase(Locale.ROOT), subcommand);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Main.getInstance().isFeatureEnabled("dtc")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.feature-disabled")));
            return true;
        }
        if (!Permission.has(sender, "command.dtc", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        Subcommand subcommand = args.length == 0 ? null : subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subcommand == null) {
            for (String line : Main.getInstance().getMessagesConfig().getStringList("help.dtc")) sender.sendMessage(C.chat(line.replace("%alias%", alias)));
            return true;
        }
        try {
            return subcommand.execute(sender, Arrays.copyOfRange(args, 1, args.length), alias);
        } catch (IllegalArgumentException exception) {
            send(sender, exception.getMessage());
        } catch (RuntimeException exception) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not execute DTC command", exception);
            send(sender, "failed");
        }
        return true;
    }

    public static void send(CommandSender sender, String key, String... replacements) {
        String message = Main.getInstance().getLocaleConfig().getString("commands.dtc." + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) message = message.replace(replacements[i], replacements[i + 1]);
        sender.sendMessage(C.chat(message));
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Main.getInstance().isFeatureEnabled("dtc")) return List.of();
        if (!Permission.has(sender, "command.dtc", "admin") || args.length == 0) return List.of();
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return subcommands.keySet().stream().filter(name -> name.startsWith(input)).sorted().toList();
        }
        Subcommand subcommand = subcommands.get(args[0].toLowerCase(Locale.ROOT));
        return subcommand == null ? List.of() : subcommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }
}
