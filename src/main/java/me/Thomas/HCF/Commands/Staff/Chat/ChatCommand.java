package me.Thomas.HCF.Commands.Staff.Chat;

import me.Thomas.HCF.Commands.Staff.Chat.Subcommands.ClearCommand;
import me.Thomas.HCF.Commands.Staff.Chat.Subcommands.PauseCommand;
import me.Thomas.HCF.Commands.Staff.Chat.Subcommands.SlowCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatCommand implements TabExecutor {

    private final Map<String, Subcommand> subcommands = new HashMap<>();
    private static boolean paused;
    private static int slowSeconds;

    public ChatCommand() {
        register(new PauseCommand());
        register(new ClearCommand());
        register(new SlowCommand());
    }

    private void register(Subcommand subcommand) {
        subcommands.put(subcommand.getName().toLowerCase(Locale.ROOT), subcommand);
        for (String alias : subcommand.getAliases()) {
            subcommands.put(alias.toLowerCase(Locale.ROOT), subcommand);
        }
    }

    public static boolean isPaused() {
        return paused;
    }

    public static boolean togglePaused() {
        paused = !paused;
        return paused;
    }

    public static int getSlowSeconds() {
        return slowSeconds;
    }

    public static void setSlowSeconds(int seconds) {
        slowSeconds = seconds;
        for (User user : Main.getInstance().getAllUsers().values()) user.removeTimer(Timer.Type.CHAT);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.chat", "staff")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        Subcommand subcommand = args.length == 0 ? null : subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subcommand != null) return subcommand.execute(sender, Arrays.copyOfRange(args, 1, args.length), alias);
        for (String line : Main.getInstance().getMessagesConfig().getStringList("help.chat")) {
            sender.sendMessage(C.chat(line.replace("%alias%", alias)));
        }
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.chat", "staff") || args.length == 0) return List.of();
        if (args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            return subcommands.keySet().stream().filter(name -> name.startsWith(input)).sorted().toList();
        }
        Subcommand subcommand = subcommands.get(args[0].toLowerCase(Locale.ROOT));
        return subcommand == null ? List.of() : subcommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }
}
