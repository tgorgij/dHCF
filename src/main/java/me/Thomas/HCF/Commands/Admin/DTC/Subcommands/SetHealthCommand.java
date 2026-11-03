package me.Thomas.HCF.Commands.Admin.DTC.Subcommands;

import me.Thomas.HCF.Commands.Admin.DTC.DTCCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.DTC.DTC;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SetHealthCommand implements Subcommand {
    @Override public String getName() { return "sethealth"; }
    @Override public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.dtc", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) { DTCCommand.send(sender, "sethealth-usage", "%alias%", alias); return true; }
        int health;
        try { health = Integer.parseInt(args[0]); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("invalid-health"); }
        DTC.get().setHealth(health);
        DTC.get().saveAsync();
        DTCCommand.send(sender, "health-set", "%health%", Integer.toString(health));
        return true;
    }
}
