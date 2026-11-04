package me.Thomas.HCF.Commands.Staff;

import me.Thomas.HCF.Events.Player.Connection.UserChatEvent;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class StaffChatCommand implements TabExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.staffchat", "staff") && !Permission.has(sender, "command.staffchat", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length > 0) {
            String message = String.join(" ", args).strip();
            if (message.isBlank()) {
                sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.staffchat.usage").replace("%alias%", alias)));
                return true;
            }
            send(sender, message);
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        boolean enabled = !UserChatEvent.isStaffChat(p.getUniqueId());
        UserChatEvent.setStaffChat(p.getUniqueId(), enabled);
        p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.staffchat.toggled")
                .replace("%state%", Main.getInstance().getLocaleConfig().getString("primary.status." + (enabled ? "enabled" : "disabled")))));
        return true;
    }

    public static void send(CommandSender sender, String message) {
        if (!Permission.has(sender, "command.staffchat", "staff") && !Permission.has(sender, "command.staffchat", "admin")) {
            if (sender instanceof Player p) UserChatEvent.setStaffChat(p.getUniqueId(), false);
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return;
        }
        Component formatted = C.chat(Main.getInstance().getLocaleConfig().getString("commands.staffchat.format")
                .replace("%player%", sender.getName()))
                .replaceText(builder -> builder.matchLiteral("%message%").replacement(Component.text(message)));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (Permission.has(staff, "command.staffchat", "staff") || Permission.has(staff, "command.staffchat", "admin")) {
                staff.sendMessage(formatted);
            }
        }
        Bukkit.getConsoleSender().sendMessage(formatted);
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        return List.of();
    }
}
