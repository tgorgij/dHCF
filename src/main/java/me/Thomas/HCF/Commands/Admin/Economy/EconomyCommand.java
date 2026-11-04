package me.Thomas.HCF.Commands.Admin.Economy;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Admin.Economy.Subcommands.SetCommand;
import me.Thomas.HCF.Commands.Admin.Economy.Subcommands.GiveCommand;
import me.Thomas.HCF.Commands.Admin.Economy.Subcommands.TakeCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EconomyCommand implements TabExecutor {

    private final Map<String, Subcommand> subcommands =
            new HashMap<>();

    private final FileConfiguration messagesConfig = Main.getInstance().getMessagesConfig();
    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    public EconomyCommand() {
        register(new SetCommand());
        register(new GiveCommand());
        register(new TakeCommand());
    }

    private void register(Subcommand subcommand) {
        subcommands.put(
                subcommand.getName().toLowerCase(Locale.ROOT),
                subcommand
        );

        for (String alias : subcommand.getAliases()) {
            subcommands.put(alias.toLowerCase(Locale.ROOT), subcommand);
        }
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command cmd,
            @NotNull String label,
            @NotNull String @NonNull [] args
    ) {
        if (!Permission.has(sender, "command.economy", "admin")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String name = args[0].toLowerCase(Locale.ROOT);
        Subcommand subcommand = subcommands.get(name);

        if (subcommand == null) {
            sendHelp(sender, label);
            return true;
        }

        String[] subcommandArgs = Arrays.copyOfRange(args, 1, args.length);

        return subcommand.execute(sender, subcommandArgs, label);
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command cmd,
            @NotNull String label,
            @NotNull String @NonNull [] args
    ) {
        if (!Permission.has(sender, "command.economy", "admin")) {
            return List.of();
        }
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();

            for (String name : subcommands.keySet()) {
                if (name.startsWith(input) && Permission.has(sender, "command.economy." + name, "admin")) {
                    suggestions.add(name);
                }
            }

            suggestions.sort(String.CASE_INSENSITIVE_ORDER);
            return suggestions;
        }

        String name = args[0].toLowerCase(Locale.ROOT);
        Subcommand subcommand = subcommands.get(name);

        if (subcommand == null) {
            return List.of();
        }

        String[] subcommandArgs = Arrays.copyOfRange(args, 1, args.length);

        return subcommand.tabComplete(sender, subcommandArgs);
    }

    private void sendHelp(CommandSender sender, String label) {
        for (String s : messagesConfig.getStringList("help.economy")) {
            if (s.contains("%alias%")) {
                s = s.replace("%alias%", label);
            }
            sender.sendMessage(C.chat(s));
        }
    }
}
