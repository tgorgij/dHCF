package me.Thomas.HCF.Commands.User;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Utils.Timer.Timer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class RequestCommand implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        String message = String.join(" ", args).strip();
        if (message.isBlank()) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.request.usage").replace("%alias%", alias)));
            return true;
        }
        if (isCoolingDown(p, Timer.Type.REQUEST)) return true;
        Component notification = C.chat(Main.getInstance().getLocaleConfig().getString("commands.request.notification")
                .replace("%player%", p.getName()))
                .replaceText(builder -> builder.matchLiteral("%message%").replacement(Component.text(message)));
        notifyStaff(notification, "request.receive");
        startCooldown(p, Timer.Type.REQUEST);
        p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.request.sent")));
        return true;
    }

    static boolean isCoolingDown(Player p, Timer.Type type) {
        Timer timer = User.get(p).getTimer(type);
        if (timer == null || timer.getRemainingTimeMillis() <= 0) return false;
        p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands." + type.name().toLowerCase(Locale.ROOT) + ".cooldown")
                .replace("%time%", C.formatChatTime((timer.getRemainingTimeMillis() + 999L) / 1000L))));
        return true;
    }

    static void startCooldown(Player p, Timer.Type type) {
        if (type.getLength() <= 0) return;
        User user = User.get(p);
        user.addTimer(type, new Timer(type));
        user.saveAsync();
    }

    static void notifyStaff(Component notification, String permission) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (Permission.has(staff, permission) || Main.getInstance().getStaffMode().isStaffOrAdmin(staff)) staff.sendMessage(notification);
        }
        Bukkit.getConsoleSender().sendMessage(notification);
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        return List.of();
    }
}
