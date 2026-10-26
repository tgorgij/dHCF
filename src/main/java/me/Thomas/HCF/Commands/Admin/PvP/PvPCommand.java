package me.Thomas.HCF.Commands.Admin.PvP;

import me.Thomas.HCF.Commands.Admin.PvP.Subcommands.EnableCommand;
import me.Thomas.HCF.Commands.Admin.PvP.Subcommands.GiveCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class PvPCommand implements TabExecutor {
    private final Map<String, Subcommand> subcommands = new HashMap<>();

    public PvPCommand() {
        register(new EnableCommand());
        register(new GiveCommand());
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
        if (Permission.has(sender, "command.pvp", "admin")) {
            for (String line : Main.getInstance().getMessagesConfig().getStringList("help.pvp")) {
                sender.sendMessage(C.chat(line.replace("%alias%", alias)));
            }
        } else {
            sender.sendMessage(C.chat(Objects.requireNonNull(Main.getInstance().getLocaleConfig()
                    .getString("commands.pvp.enable.usage")).replace("%alias%", alias)));
        }
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 0) return List.of();
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            if (sender instanceof Player) names.add("enable");
            if (Permission.has(sender, "command.pvp", "admin")) names.add("give");
            String input = args[0].toLowerCase(Locale.ROOT);
            return names.stream().filter(name -> name.startsWith(input)).toList();
        }
        Subcommand subcommand = subcommands.get(args[0].toLowerCase(Locale.ROOT));
        return subcommand == null ? List.of() : subcommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }
}
