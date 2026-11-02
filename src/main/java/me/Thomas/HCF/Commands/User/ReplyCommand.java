package me.Thomas.HCF.Commands.User;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public class ReplyCommand implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        String message = String.join(" ", args).strip();
        if (message.isBlank()) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.reply.usage").replace("%alias%", alias)));
            return true;
        }
        UUID target = User.get(p).getLastConversation();
        if (target == null) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.reply.no-conversation")));
            return true;
        }
        MessageCommand.send(p, Bukkit.getPlayer(target), message);
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        return List.of();
    }
}
