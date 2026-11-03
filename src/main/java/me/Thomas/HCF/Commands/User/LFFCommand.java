package me.Thomas.HCF.Commands.User;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LFFCommand implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        User user = User.get(p);
        if (user.hasFaction()) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.lff.in-faction")));
            return true;
        }
        String message = String.join(" ", args).strip();
        int maximum = Math.clamp(Main.getInstance().getConfig().getInt("lff.max-message-length", 120), 1, 256);
        if (message.length() > maximum) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.lff.too-long")
                    .replace("%length%", Integer.toString(maximum))));
            return true;
        }
        Timer timer = user.getTimer(Timer.Type.LFF);
        if (timer != null && timer.getRemainingTimeMillis() > 0) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.lff.cooldown")
                    .replace("%time%", C.formatChatTime((timer.getRemainingTimeMillis() + 999L) / 1000L))));
            return true;
        }
        String path = message.isEmpty() ? "commands.lff.format" : "commands.lff.message-format";
        Component announcement = C.chat(Main.getInstance().getLocaleConfig().getString(path).replace("%player%", p.getName()))
                .replaceText(builder -> builder.matchLiteral("%message%").replacement(Component.text(message)));
        Bukkit.broadcast(announcement);
        if (Timer.Type.LFF.getLength() > 0) {
            user.addTimer(Timer.Type.LFF, new Timer(Timer.Type.LFF));
            user.saveAsync();
        }
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        return List.of();
    }
}
