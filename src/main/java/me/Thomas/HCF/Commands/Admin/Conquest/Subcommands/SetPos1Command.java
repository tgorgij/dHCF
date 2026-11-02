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
import org.bukkit.entity.Player;
import java.util.Arrays;
import java.util.Locale;

public class SetPos1Command implements Subcommand {

    @Override
    public String getName() { return "setpos1"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.conquest", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) {
            ConquestCommand.send(sender, "setpos1-usage", "%alias%", alias);
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        Conquest.Color color = Conquest.color(args[0]);
        Conquest.get().setPosition(color, true, p.getLocation());
        Conquest.get().saveAsync();
        ConquestCommand.send(sender, "position-set", "%zone%", color.name(), "%position%", "1");
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
