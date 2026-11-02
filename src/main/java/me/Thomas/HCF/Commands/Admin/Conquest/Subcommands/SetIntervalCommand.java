package me.Thomas.HCF.Commands.Admin.Conquest.Subcommands;

import me.Thomas.HCF.Commands.Admin.Conquest.ConquestCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Conquest.Conquest;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.Arrays;
import java.util.Locale;

public class SetIntervalCommand implements Subcommand {

    @Override
    public String getName() { return "setinterval"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.conquest", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 2) {
            ConquestCommand.send(sender, "setinterval-usage", "%alias%", alias);
            return true;
        }
        Conquest.Color color = Conquest.color(args[0]);
        int seconds;
        try { seconds = Integer.parseInt(args[1]); }
        catch (NumberFormatException exception) { ConquestCommand.send(sender, "invalid-interval"); return true; }
        Conquest.get().setInterval(color, seconds);
        Conquest.get().saveAsync();
        ConquestCommand.send(sender, "interval-set", "%zone%", color.name(), "%time%", Integer.toString(seconds));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.conquest", "admin") || args.length != 1) return List.of();
        String input = args[0].toLowerCase(Locale.ROOT);
        return Arrays.stream(Conquest.Color.values()).map(color -> color.name().toLowerCase(Locale.ROOT))
                .filter(name -> name.startsWith(input)).sorted().toList();
    }
}
