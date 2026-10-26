package me.Thomas.HCF.Commands.Admin.Lives;

import me.Thomas.HCF.Commands.Admin.Lives.Subcommands.AddCommand;
import me.Thomas.HCF.Commands.Admin.Lives.Subcommands.SetCommand;
import me.Thomas.HCF.Commands.Admin.Lives.Subcommands.TakeCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LivesCommand implements TabExecutor {
    private final Map<String, Subcommand> subcommands = Map.of(
            "set", new SetCommand(), "add", new AddCommand(), "take", new TakeCommand());

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.lives", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        Subcommand subcommand = args.length == 0 ? null : subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subcommand == null) {
            for (String line : Main.getInstance().getMessagesConfig().getStringList("help.lives")) {
                sender.sendMessage(C.chat(line.replace("%alias%", alias)));
            }
            return true;
        }
        return subcommand.execute(sender, Arrays.copyOfRange(args, 1, args.length), alias);
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.lives", "admin") || args.length == 0) return List.of();
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return subcommands.keySet().stream().filter(name -> name.startsWith(input)).sorted().toList();
        }
        Subcommand subcommand = subcommands.get(args[0].toLowerCase(Locale.ROOT));
        return subcommand == null ? List.of() : subcommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }
}
