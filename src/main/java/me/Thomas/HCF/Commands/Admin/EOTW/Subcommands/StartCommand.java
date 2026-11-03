package me.Thomas.HCF.Commands.Admin.EOTW.Subcommands;

import me.Thomas.HCF.Commands.Admin.EOTW.EOTWCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class StartCommand implements Subcommand {
    @Override
    public String getName() { return "start"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-console")));
            return true;
        }
        if (args.length != 1) { EOTWCommand.send(sender, "start.usage", "%alias%", alias); return true; }
        long seconds;
        try {
            seconds = Long.parseLong(args[0]);
            if (seconds < 0 || seconds > Integer.MAX_VALUE) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.invalid-time")));
            return true;
        }
        try {
            Main.getInstance().startEOTW(seconds);
        } catch (IllegalArgumentException exception) {
            EOTWCommand.send(sender, exception.getMessage());
            return true;
        }
        EOTWCommand.send(sender, "start.success", "%time%", C.formatChatTime(seconds));
        return true;
    }
}
