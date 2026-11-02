package me.Thomas.HCF.Commands.User;

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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ReportCommand implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        String reason = args.length < 2 ? "" : String.join(" ", Arrays.copyOfRange(args, 1, args.length)).strip();
        if (reason.isBlank()) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.report.usage").replace("%alias%", alias)));
            return true;
        }
        if (RequestCommand.isCoolingDown(p, Timer.Type.REPORT)) return true;
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !p.canSee(target)) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        if (p.getUniqueId().equals(target.getUniqueId())) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.report.self")));
            return true;
        }
        Component notification = C.chat(Main.getInstance().getLocaleConfig().getString("commands.report.notification")
                .replace("%player%", p.getName()).replace("%target%", target.getName()))
                .replaceText(builder -> builder.matchLiteral("%reason%").replacement(Component.text(reason)));
        RequestCommand.notifyStaff(notification, "report.receive");
        RequestCommand.startCooldown(p, Timer.Type.REPORT);
        p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.report.sent").replace("%player%", target.getName())));
        return true;
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
