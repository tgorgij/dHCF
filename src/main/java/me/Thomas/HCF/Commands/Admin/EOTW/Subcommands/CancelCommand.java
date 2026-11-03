package me.Thomas.HCF.Commands.Admin.EOTW.Subcommands;

import me.Thomas.HCF.Commands.Admin.EOTW.EOTWCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CancelCommand implements Subcommand {
    @Override
    public String getName() { return "cancel"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-console")));
            return true;
        }
        if (args.length != 0) { EOTWCommand.send(sender, "cancel.usage", "%alias%", alias); return true; }
        if (!Main.getInstance().cancelEOTW()) { EOTWCommand.send(sender, "not-running"); return true; }
        EOTWCommand.send(sender, "cancel.success");
        return true;
    }
}
