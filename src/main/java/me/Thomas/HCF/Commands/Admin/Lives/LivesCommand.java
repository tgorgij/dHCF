package me.Thomas.HCF.Commands.Admin.Lives;

import me.Thomas.HCF.Commands.Admin.Lives.Subcommands.AddCommand;
import me.Thomas.HCF.Commands.Admin.Lives.Subcommands.CheckCommand;
import me.Thomas.HCF.Commands.Admin.Lives.Subcommands.SendCommand;
import me.Thomas.HCF.Commands.Admin.Lives.Subcommands.SetCommand;
import me.Thomas.HCF.Commands.Admin.Lives.Subcommands.TakeCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LivesCommand implements TabExecutor {
    private final Map<String, Subcommand> subcommands = new HashMap<>();

    public LivesCommand() {
        register(new SetCommand());
        register(new AddCommand());
        register(new TakeCommand());
        register(new CheckCommand());
        register(new SendCommand());
    }

    private void register(Subcommand subcommand) {
        subcommands.put(subcommand.getName().toLowerCase(Locale.ROOT), subcommand);
        for (String alias : subcommand.getAliases()) {
            subcommands.put(alias.toLowerCase(Locale.ROOT), subcommand);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        Subcommand subcommand = args.length == 0 ? null : subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subcommand != null) return subcommand.execute(sender, Arrays.copyOfRange(args, 1, args.length), alias);
        if (!Permission.has(sender, "command.lives", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig()
                    .getString("commands.lives.usage").replace("%alias%", alias)));
            return true;
        }
        for (String line : Main.getInstance().getMessagesConfig().getStringList("help.lives")) {
            sender.sendMessage(C.chat(line.replace("%alias%", alias)));
        }
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 0) return List.of();
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return subcommands.keySet().stream()
                    .filter(name -> (name.equals("check") || name.equals("send")) ? sender instanceof Player : Permission.has(sender, "command.lives", "admin"))
                    .filter(name -> name.startsWith(input)).sorted().toList();
        }
        Subcommand subcommand = subcommands.get(args[0].toLowerCase(Locale.ROOT));
        return subcommand == null ? List.of() : subcommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }
}
