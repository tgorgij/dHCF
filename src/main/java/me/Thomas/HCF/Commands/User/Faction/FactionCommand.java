package me.Thomas.HCF.Commands.User.Faction;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Commands.User.Faction.Subcommands.*;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
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

public class FactionCommand implements TabExecutor {

    private final Map<String, Subcommand> subcommands =
            new HashMap<>();

    private final FileConfiguration messagesConfig = Main.getInstance().getMessagesConfig();

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
        register(new InvitesCommand());
        register(new LeaveCommand());
        register(new DepositCommand());
        register(new RenameCommand());
        register(new ClaimforCommand());
        register(new SetDeathbanCommand());
        register(new BypassCommand());
        register(new ClaimCommand());
        register(new SetHomeCommand());
        register(new HomeCommand());
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
        if (args.length == 0) {
            return List.of();
        }

        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();

            for (String name : subcommands.keySet()) {
                String normalizedName = name.toLowerCase(Locale.ROOT);

                if (normalizedName.startsWith(input)) {
                    suggestions.add(name.toLowerCase(Locale.ROOT)
                    );
                }
            }

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
        for (String s : messagesConfig.getStringList("factions.help.main")) {
            if (s.contains("%alias%")) {
                s = s.replace("%alias%", label);
            }
            sender.sendMessage(C.chat(s));
        }
    }
}