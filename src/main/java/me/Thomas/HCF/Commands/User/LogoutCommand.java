package me.Thomas.HCF.Commands.User;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class LogoutCommand implements TabExecutor {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.logout.usage")
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
        start(player, Math.max(0, Timer.Type.LOGOUT.getLength()));
        return true;
    }

    public static void start(Player player, long seconds) {
        User user = User.get(player);
        LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();
        Timer timer = new Timer(Timer.Type.LOGOUT, System.currentTimeMillis() + seconds * 1000L, 0L, false);
        user.addTimer(Timer.Type.LOGOUT, timer);
        player.sendMessage(C.chat(localeConfig.getString("commands.logout.started")
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
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        return List.of();
    }
}
