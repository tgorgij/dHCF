package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import java.util.List;

public final class SubclaimCommand implements Subcommand {
    @Override public String getName() { return "subclaim"; }
    @Override public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        for (String line : Main.getInstance().getMessagesConfig().getStringList("factions.help.subclaiming")) {
            sender.sendMessage(C.chat(line.replace("%alias%", alias)));
        }
        return true;
    }
}
