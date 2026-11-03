package me.Thomas.HCF.Commands.Admin.Timer;

import me.Thomas.HCF.Commands.Admin.Timer.Subcommands.AddCommand;
import me.Thomas.HCF.Commands.Admin.Timer.Subcommands.RemoveCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Utils.Timer.Timer;
import me.Thomas.HCF.Utils.Ability.AbilityItems;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TimerCommand implements TabExecutor {

    private final Map<String, Subcommand> subcommands = new HashMap<>();

    public TimerCommand() {
        register(new AddCommand());
        register(new RemoveCommand());
    }

    private void register(Subcommand subcommand) {
        subcommands.put(subcommand.getName(), subcommand);
        for (String alias : subcommand.getAliases()) subcommands.put(alias, subcommand);
    }

    public static Timer.Type getType(String name) {
        try {
            return Timer.Type.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static List<String> getTimerNames(boolean adding) {
        List<String> names = new ArrayList<>();
        for (Timer.Type type : Timer.Type.values()) {
            if (adding && (type == Timer.Type.HOME || type == Timer.Type.STUCK || type == Timer.Type.LOGOUT)) continue;
            names.add(type.name().toLowerCase(Locale.ROOT));
        }
        names.add("ability");
        for (AbilityItems.Type type : AbilityItems.Type.values()) names.add(type.getId());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static void send(CommandSender sender, String key, String... replacements) {
        String message = Main.getInstance().getLocaleConfig().getString("commands.timer." + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) message = message.replace(replacements[i], replacements[i + 1]);
        sender.sendMessage(C.chat(message));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.timer", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        Subcommand subcommand = args.length == 0 ? null : subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subcommand != null) return subcommand.execute(sender, Arrays.copyOfRange(args, 1, args.length), alias);
        for (String line : Main.getInstance().getMessagesConfig().getStringList("help.timer")) sender.sendMessage(C.chat(line.replace("%alias%", alias)));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.timer", "admin") || args.length == 0) return List.of();
        if (args.length == 1) return subcommands.keySet().stream().filter(name -> name.startsWith(args[0].toLowerCase(Locale.ROOT))).sorted().toList();
        Subcommand subcommand = subcommands.get(args[0].toLowerCase(Locale.ROOT));
        return subcommand == null ? List.of() : subcommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }
}
