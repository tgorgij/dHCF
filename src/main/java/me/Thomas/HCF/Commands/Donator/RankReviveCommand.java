package me.Thomas.HCF.Commands.Donator;

import me.Thomas.HCF.IO.Database.SaveSnapshot;
import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Deathban.Deathban;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public class RankReviveCommand implements TabExecutor {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    private long cooldown(Player player) {
        long duration = Long.MAX_VALUE;
        boolean matched = false;
        for (Map<?, ?> entry : Main.getInstance().getConfig().getMapList("rank-revive.permissions")) {
            if (entry.get("permission") instanceof String permission
                    && entry.get("cooldown") instanceof Number seconds && player.hasPermission(permission)) {
                duration = Math.min(duration, Math.max(0L, seconds.longValue()));
                matched = true;
            }
        }
        return matched ? duration : -1L;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Main.getInstance().isFeatureEnabled("deathbans")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.feature-disabled")));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
            return true;
        }
        long seconds = cooldown(p);
        if (seconds < 0) {
            p.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) {
            p.sendMessage(C.chat(localeConfig.getString("commands.rankrevive.usage").replace("%alias%", alias)));
            return true;
        }
        User user = User.get(p);
        Timer previousTimer = user.getTimer(Timer.Type.RANKREVIVE);
        if (previousTimer != null && previousTimer.getRemainingTimeMillis() > 0) {
            p.sendMessage(C.chat(localeConfig.getString("commands.rankrevive.cooldown")
                    .replace("%time%", C.formatChatTime((previousTimer.getRemainingTimeMillis() + 999L) / 1000L))));
            return true;
        }
        User target = User.get(args[0]);
        if (target == null) {
            p.sendMessage(C.chat(localeConfig.getString("primary.no-player")));
            return true;
        }
        if (target.getUUID().equals(p.getUniqueId())) {
            p.sendMessage(C.chat(localeConfig.getString("commands.rankrevive.self")));
            return true;
        }
        long previousDeathban = target.getDeathbanExpiry();
        long now = System.currentTimeMillis();
        if (previousDeathban <= now) {
            p.sendMessage(C.chat(localeConfig.getString("commands.rankrevive.not-deathbanned")
                    .replace("%player%", target.getName())));
            return true;
        }
        target.setDeathbanExpiry(0L);
        if (seconds > 0) user.addTimer(Timer.Type.RANKREVIVE,
                new Timer(Timer.Type.RANKREVIVE, Deathban.getExpiry(now, seconds), 0L, false));
        else user.removeTimer(Timer.Type.RANKREVIVE);
        List<SaveSnapshot> snapshots = new ArrayList<>();
        SaveSnapshot userSnapshot = user.snapshot();
        if (userSnapshot != null) snapshots.add(userSnapshot);
        SaveSnapshot targetSnapshot = target.snapshot();
        if (targetSnapshot != null) snapshots.add(targetSnapshot);
        try {
            Main.getInstance().getDatabaseManager().save(snapshots).join();
        } catch (RuntimeException exception) {
            target.setDeathbanExpiry(previousDeathban);
            if (previousTimer == null) user.removeTimer(Timer.Type.RANKREVIVE);
            else user.addTimer(Timer.Type.RANKREVIVE, previousTimer);
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not save rank revive for " + p.getUniqueId(), exception);
            p.sendMessage(C.chat(localeConfig.getString("commands.rankrevive.failed")));
            return true;
        }
        p.sendMessage(C.chat(localeConfig.getString("commands.rankrevive.success").replace("%player%", target.getName())));
        Player recipient = Bukkit.getPlayer(target.getUUID());
        if (recipient != null) recipient.sendMessage(C.chat(localeConfig.getString("commands.rankrevive.received")
                .replace("%player%", p.getName())));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Main.getInstance().isFeatureEnabled("deathbans") || !(sender instanceof Player p)
                || cooldown(p) < 0 || args.length != 1) return List.of();
        String input = args[0].toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        return Main.getInstance().getAllUsers().values().stream()
                .filter(user -> !user.getUUID().equals(p.getUniqueId()) && user.getDeathbanExpiry() > now)
                .map(User::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
