package me.Thomas.HCF.Commands.Staff.Notes.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import net.kyori.adventure.text.Component;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ListCommand implements Subcommand {
    @Override
    public String getName() { return "list"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (args.length != 1) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.notes.list-usage").replace("%alias%", alias)));
            return true;
        }
        User target = User.get(args[0]);
        sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.notes.header").replace("%player%", target.getName())));
        if (target.getNotes().isEmpty()) sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.notes.empty")));
        for (User.Note note : target.getNotes()) {
            Component line = C.chat(Main.getInstance().getLocaleConfig().getString("commands.notes.entry")
                    .replace("%id%", Integer.toString(note.id())).replace("%author%", note.author())
                    .replace("%time%", C.formatChatTime(Math.max(0L, System.currentTimeMillis() - note.createdAt()) / 1000L)))
                    .replaceText(builder -> builder.matchLiteral("%note%").replacement(Component.text(note.text())));
            sender.sendMessage(line);
        }
        return true;
    }
}
