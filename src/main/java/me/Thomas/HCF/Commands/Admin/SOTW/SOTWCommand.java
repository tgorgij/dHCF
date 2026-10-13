package me.Thomas.HCF.Commands.Admin.SOTW;

import me.Thomas.HCF.Commands.Admin.SOTW.Subcommands.*;
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

import java.util.*;

public class SOTWCommand implements TabExecutor {

    private final Map<String, Subcommand> subcommands =
            new HashMap<>();

    private final FileConfiguration messagesConfig = Main.getInstance().getMessagesConfig();
    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    public SOTWCommand() {
        register(new AddCommand());
        register(new CancelCommand());
        register(new EnableCommand());
        register(new PauseCommand());
        register(new StartCommand());
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
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String name = args[0].toLowerCase(Locale.ROOT);
        Subcommand subcommand = subcommands.get(name);

        if (subcommand == null) {
            sendHelp(sender);
            return true;
        }

        String[] subcommandArgs = Arrays.copyOfRange(args, 1, args.length);

        return subcommand.execute(sender, subcommandArgs, label);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        return List.of();
    }

    private void sendHelp(CommandSender sender) {
        if (!Permission.has(sender, "command.sotw", "admin")) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.sotw.alt-usage"))));
            return;
        }
        for (String s : messagesConfig.getStringList("help.sotw")) {
            sender.sendMessage(C.chat(s));
        }
    }
}
