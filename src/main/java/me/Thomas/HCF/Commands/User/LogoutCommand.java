package me.Thomas.HCF.Commands.User;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class LogoutCommand implements TabExecutor {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.logout.usage"))
                    .replace("%alias%", alias)));
            return true;
        }
        User user = User.get(player);
        if (user.hasTimer(Timer.Type.COMBAT)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.combat-tagged")));
            return true;
        }
        if (user.getTimer(Timer.Type.LOGOUT) != null) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.logout.already")));
            return true;
        }
        int seconds = Math.max(0, Timer.Type.LOGOUT.getLength());
        Timer timer = new Timer(Timer.Type.LOGOUT);
        user.addTimer(Timer.Type.LOGOUT, timer);
        sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.logout.started"))
                .replace("%time%", C.formatChatTime(seconds))));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (user.getTimer(Timer.Type.LOGOUT) != timer) return;
                user.removeTimer(Timer.Type.LOGOUT);
                if (!player.isOnline() || User.get(player) != user) return;
                if (user.hasTimer(Timer.Type.COMBAT)) {
                    player.sendMessage(C.chat(localeConfig.getString("commands.logout.cancel-combat")));
                    return;
                }
                player.kick(C.chat(localeConfig.getString("commands.logout.completed")));
            }
        }.runTaskLater(Main.getInstance(), seconds * 20L);
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        return List.of();
    }
}
