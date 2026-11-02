package me.Thomas.HCF.Commands.Admin.Schedule;

import me.Thomas.HCF.Commands.Admin.Schedule.Subcommands.AddCommand;
import me.Thomas.HCF.Commands.Admin.Schedule.Subcommands.ListCommand;
import me.Thomas.HCF.Commands.Admin.Schedule.Subcommands.RemoveCommand;
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

public class ScheduleCommand implements TabExecutor {

    private final Map<String, Subcommand> subcommands = new HashMap<>();

    public ScheduleCommand() {
        register(new AddCommand());
        register(new ListCommand());
        register(new RemoveCommand());
    }

    private void register(Subcommand subcommand) {
        subcommands.put(subcommand.getName(), subcommand);
        for (String alias : subcommand.getAliases()) subcommands.put(alias, subcommand);
    }

    public static void send(CommandSender sender, String key, String... replacements) {
        String message = Main.getInstance().getLocaleConfig().getString("commands.schedule." + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) message = message.replace(replacements[i], replacements[i + 1]);
        sender.sendMessage(C.chat(message));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.schedule", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        Subcommand subcommand = args.length == 0 ? null : subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subcommand == null) {
            for (String line : Main.getInstance().getMessagesConfig().getStringList("help.schedule")) sender.sendMessage(C.chat(line.replace("%alias%", alias)));
            return true;
        }
        try {
            return subcommand.execute(sender, Arrays.copyOfRange(args, 1, args.length), alias);
        } catch (IllegalArgumentException exception) {
            send(sender, exception.getMessage());
        } catch (RuntimeException exception) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not update event schedule", exception);
            send(sender, "failed");
        }
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.schedule", "admin") || args.length == 0) return List.of();
        if (args.length == 1) return subcommands.keySet().stream().filter(name -> name.startsWith(args[0].toLowerCase(Locale.ROOT))).sorted().toList();
        Subcommand subcommand = subcommands.get(args[0].toLowerCase(Locale.ROOT));
        return subcommand == null ? List.of() : subcommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }
}
