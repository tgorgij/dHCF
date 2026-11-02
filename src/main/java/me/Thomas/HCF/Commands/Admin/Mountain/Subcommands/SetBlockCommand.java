package me.Thomas.HCF.Commands.Admin.Mountain.Subcommands;

import me.Thomas.HCF.Commands.Admin.Mountain.MountainCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Mountain.Mountain;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import org.bukkit.Material;
import java.util.Arrays;
import java.util.Locale;

public class SetBlockCommand implements Subcommand {

    @Override
    public String getName() { return "setblock"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.mountain", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 2) {
            MountainCommand.send(sender, "setblock-usage", "%alias%", alias, "%mountain%", args.length == 0 ? "<name>" : args[0]);
            return true;
        }
        Mountain mountain = Mountain.get(args[0]);
        mountain.setBlock(Material.matchMaterial(args[1]));
        mountain.saveAsync();
        MountainCommand.send(sender, "block-set", "%mountain%", mountain.getName(), "%block%", mountain.getBlock().name());
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.mountain", "admin") || args.length != 2) return List.of();
        String input = args[1].toLowerCase(Locale.ROOT);
        return Arrays.stream(Material.values()).filter(material -> !material.isLegacy() && material.isBlock() && !material.isAir())
                .map(material -> material.name().toLowerCase(Locale.ROOT)).filter(name -> name.startsWith(input)).sorted().toList();
    }
}
