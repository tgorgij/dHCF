package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

public class StuckCommand implements Subcommand {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "stuck";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("primary.not-player"))));
            return true;
        }
        if (args.length != 0) {
            player.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.stuck.usage"))
                    .replace("%alias%", alias)));
            return true;
        }
        User user = User.get(player);
        if (user.hasTimer(Timer.Type.COMBAT)) {
            player.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("primary.combat-tagged"))));
            return true;
        }
        if (user.getTimer(Timer.Type.HOME) != null || user.getTimer(Timer.Type.STUCK) != null) {
            player.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("primary.teleport.already"))));
            return true;
        }
        Timer timer = new Timer(Timer.Type.STUCK);
        user.addTimer(Timer.Type.STUCK, timer);
        player.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.stuck.success"))
                .replace("%time%", C.formatChatTime(Timer.Type.STUCK.getLength()))));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (user.getTimer(Timer.Type.STUCK) != timer) return;
                user.removeTimer(Timer.Type.STUCK);
                if (!player.isOnline() || user.hasTimer(Timer.Type.COMBAT)) return;

                // Stuck teleportation will be implemented here.
            }
        }.runTaskLater(Main.getInstance(), Timer.Type.STUCK.getLength() * 20L);
        return true;
    }
}
