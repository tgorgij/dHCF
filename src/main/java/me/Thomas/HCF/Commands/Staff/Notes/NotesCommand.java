package me.Thomas.HCF.Commands.Staff.Notes;

import me.Thomas.HCF.Commands.Staff.Notes.Subcommands.AddCommand;
import me.Thomas.HCF.Commands.Staff.Notes.Subcommands.ListCommand;
import me.Thomas.HCF.Commands.Staff.Notes.Subcommands.RemoveCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.User.User;
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

public class NotesCommand implements TabExecutor {
    private final Map<String, Subcommand> subcommands = new HashMap<>();

    public NotesCommand() {
        register(new AddCommand());
        register(new RemoveCommand());
        register(new ListCommand());
    }

    private void register(Subcommand subcommand) {
        subcommands.put(subcommand.getName(), subcommand);
        for (String alias : subcommand.getAliases()) subcommands.put(alias, subcommand);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.notes", "staff")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        Subcommand subcommand = args.length < 2 ? null : subcommands.get(args[1].toLowerCase(Locale.ROOT));
        if (subcommand == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.notes.usage").replace("%alias%", alias)));
            return true;
        }
        if (User.get(args[0]) == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        String[] values = new String[args.length - 1];
        values[0] = args[0];
        System.arraycopy(args, 2, values, 1, args.length - 2);
        return subcommand.execute(sender, values, alias);
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.notes", "staff") || args.length == 0) return List.of();
        String input = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) return Main.getInstance().getAllUsers().values().stream().map(User::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        if (args.length == 2) return subcommands.keySet().stream().filter(name -> name.startsWith(input)).sorted().toList();
        User target = User.get(args[0]);
        if (args.length == 3 && args[1].equalsIgnoreCase("remove") && target != null) {
            return target.getNotes().stream().map(note -> Integer.toString(note.id())).filter(id -> id.startsWith(input)).toList();
        }
        return List.of();
    }
}
