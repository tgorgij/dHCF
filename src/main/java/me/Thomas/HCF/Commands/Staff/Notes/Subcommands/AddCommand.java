package me.Thomas.HCF.Commands.Staff.Notes.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AddCommand implements Subcommand {
    @Override
    public String getName() { return "add"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (args.length < 2) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.notes.add-usage").replace("%alias%", alias)));
            return true;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).strip();
        if (text.isBlank() || text.length() > 1024) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.notes.invalid-text")));
            return true;
        }
        User target = User.get(args[0]);
        User.Note note = target.addNote(sender.getName(), text);
        target.saveAsync();
        sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.notes.added")
                .replace("%player%", target.getName()).replace("%id%", Integer.toString(note.id()))));
        return true;
    }
}
