package me.Thomas.HCF.Commands.User;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MessageCommand implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        String message = args.length < 2 ? "" : String.join(" ", Arrays.copyOfRange(args, 1, args.length)).strip();
        if (message.isBlank()) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.message.usage").replace("%alias%", alias)));
            return true;
        }
        send(p, Bukkit.getPlayerExact(args[0]), message);
        return true;
    }

    public static void send(Player p, Player target, String message) {
        if (target == null || !target.isOnline() || !p.canSee(target)) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return;
        }
        if (p.getUniqueId().equals(target.getUniqueId())) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.message.self")));
            return;
        }
        User recipient = User.get(target);
        if (recipient.isIgnoring(p.getUniqueId())) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.message.ignored")
                    .replace("%player%", target.getName())));
            return;
        }
        if (!recipient.isMessages()) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.message.disabled")
                    .replace("%player%", target.getName())));
            return;
        }
        p.sendMessage(format("sent", target.getName(), message));
        target.sendMessage(format("received", p.getName(), message));
        User.get(p).setLastConversation(target.getUniqueId());
        recipient.setLastConversation(p.getUniqueId());
        Component spyMessage = C.chat(Main.getInstance().getLocaleConfig().getString("commands.socialspy.format")
                .replace("%sender%", p.getName()).replace("%recipient%", target.getName()))
                .replaceText(builder -> builder.matchLiteral("%message%").replacement(Component.text(message)));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (User.get(staff).isSocialSpy() && Permission.has(staff, "command.socialspy", "staff")) staff.sendMessage(spyMessage);
        }
        if (recipient.isMessageSounds()) {
            target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
        }
    }

    private static Component format(String direction, String player, String message) {
        return C.chat(Main.getInstance().getLocaleConfig().getString("commands.message." + direction).replace("%player%", player))
                .replaceText(builder -> builder.matchLiteral("%message%").replacement(Component.text(message)));
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player p) || args.length != 1) return List.of();
        String input = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().filter(target -> !target.getUniqueId().equals(p.getUniqueId()))
                .filter(p::canSee).map(Player::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
