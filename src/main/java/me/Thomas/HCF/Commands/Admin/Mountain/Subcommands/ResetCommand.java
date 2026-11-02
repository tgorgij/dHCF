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

public class ResetCommand implements Subcommand {

    @Override
    public String getName() { return "reset"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.mountain", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) {
            MountainCommand.send(sender, "reset-usage", "%alias%", alias, "%mountain%", args.length == 0 ? "<name>" : args[0]);
            return true;
        }
        Mountain mountain = Mountain.get(args[0]);
        mountain.reset();
        mountain.saveAsync();
        MountainCommand.send(sender, "reset", "%mountain%", mountain.getName());
        return true;
    }
}
