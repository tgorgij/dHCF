package me.Thomas.HCF.Commands.User.Faction;

import me.Thomas.HCF.Commands.User.Faction.Subcommands.*;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class FactionCommand implements TabExecutor {

    private final Map<String, FactionSubcommand> subcommands =
            new HashMap<>();

    public FactionCommand() {
        register(new CreateCommand());
        register(new CreateSystemCommand());
        register(new ShowCommand());
        register(new SetColorCommand());
        register(new SetTypeCommand());
        register(new SetLocationCommand());
        register(new DebugCommand());
        register(new DisbandCommand());
        register(new ForceDisband());
        register(new InviteCommand());
        register(new UninviteCommand());
        register(new JoinCommand());
        register(new OpenCommand());
    }

    private void register(FactionSubcommand subcommand) {
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
        if (args.length == 0) {
            sender.sendMessage(C.chat(
                    Objects.requireNonNull(
                            Main.getInstance()
                                    .getLocaleConfig()
                                    .getString("commands.faction.usage")
                    ).replace("%alias%", label)
            ));
            return true;
        }

        String name = args[0].toLowerCase(Locale.ROOT);
        FactionSubcommand subcommand = subcommands.get(name);

        if (subcommand == null) {
            sender.sendMessage(C.chat(
                    Objects.requireNonNull(
                            Main.getInstance()
                                    .getLocaleConfig()
                                    .getString("commands.faction.usage")
                    ).replace("%alias%", label)
            ));
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
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();

            for (String name : subcommands.keySet()) {
                if (name.startsWith(input)) {
                    suggestions.add(name);
                }
            }

            return suggestions;
        }

        String name = args[0].toLowerCase(Locale.ROOT);
        FactionSubcommand subcommand = subcommands.get(name);

        if (subcommand == null) {
            return List.of();
        }

        String[] subcommandArgs = Arrays.copyOfRange(args, 1, args.length);

        return subcommand.tabComplete(sender, subcommandArgs);
    }
}