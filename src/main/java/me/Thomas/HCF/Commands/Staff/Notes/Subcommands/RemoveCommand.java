package me.Thomas.HCF.Commands.Staff.Notes.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RemoveCommand implements Subcommand {
    @Override
    public String getName() { return "remove"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (args.length != 2) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.notes.remove-usage").replace("%alias%", alias)));
            return true;
        }
        int id;
        try { id = Integer.parseInt(args[1]); }
        catch (NumberFormatException exception) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.notes.not-found")));
            return true;
        }
        User target = User.get(args[0]);
        if (!target.removeNote(id)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.notes.not-found")));
            return true;
        }
        target.saveAsync();
        sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.notes.removed")
                .replace("%player%", target.getName()).replace("%id%", Integer.toString(id))));
        return true;
    }
}
