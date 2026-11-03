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

public class CancelCommand implements Subcommand {
    @Override public String getName() { return "cancel"; }
    @Override public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.dtc", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 0) { DTCCommand.send(sender, "cancel-usage", "%alias%", alias); return true; }
        if (!DTC.get().isRunning()) throw new IllegalArgumentException("not-running");
        DTC.get().cancel();
        return true;
    }
}
