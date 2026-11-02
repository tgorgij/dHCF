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

public class SetIntervalCommand implements Subcommand {

    @Override
    public String getName() { return "setinterval"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.mountain", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 2) {
            MountainCommand.send(sender, "setinterval-usage", "%alias%", alias, "%mountain%", args.length == 0 ? "<name>" : args[0]);
            return true;
        }
        Mountain mountain = Mountain.get(args[0]);
        int minutes;
        try { minutes = Integer.parseInt(args[1]); }
        catch (NumberFormatException exception) { MountainCommand.send(sender, "invalid-interval"); return true; }
        mountain.setInterval(minutes);
        mountain.saveAsync();
        MountainCommand.send(sender, "interval-set", "%mountain%", mountain.getName(), "%interval%", Integer.toString(minutes));
        return true;
    }
}
