package me.Thomas.HCF.Commands.Admin.Timer.Subcommands;

import me.Thomas.HCF.Commands.Admin.Timer.TimerCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Commands.User.LogoutCommand;
import me.Thomas.HCF.Commands.User.Faction.Subcommands.HomeCommand;
import me.Thomas.HCF.Commands.User.Faction.Subcommands.StuckCommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Ability.AbilityItems;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Utils.Timer.ServerTimer;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class AddCommand implements Subcommand {
    @Override
    public String getName() { return "add"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.timer", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length < 2 || args.length > 3) { TimerCommand.send(sender, "add-usage", "%alias%", alias); return true; }
        User user = User.get(args[0]);
        if (user == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        Timer.Type type = TimerCommand.getType(args[1]);
        AbilityItems.Type ability = AbilityItems.getType(args[1]);
        boolean global = args[1].equalsIgnoreCase("ability");
        if (type == null && ability == null && !global) { TimerCommand.send(sender, "invalid-timer"); return true; }
        long seconds;
        long now = System.currentTimeMillis();
        long expires;
        try {
            seconds = args.length == 3 ? Long.parseLong(args[2]) : type != null ? type.getLength()
                    : Main.getInstance().getConfig().getInt(global ? "abilities.global-cooldown" : ability.path() + ".cooldown", global ? 15 : 60);
            if (seconds <= 0) throw new NumberFormatException();
            expires = Math.addExact(now, Math.multiplyExact(seconds, 1000L));
        } catch (NumberFormatException | ArithmeticException exception) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.invalid-time")));
            return true;
        }
        if (type == Timer.Type.HOME || type == Timer.Type.LOGOUT || type == Timer.Type.STUCK) {
            Player player = Bukkit.getPlayer(user.getUUID());
            if (player == null || !player.isOnline() || player.isDead()) {
                TimerCommand.send(sender, "unavailable");
                return true;
            }
            if (type == Timer.Type.HOME) {
                Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
                if (faction == null || faction.getHome() == null) {
                    TimerCommand.send(sender, "no-home", "%player%", user.getName());
                    return true;
                }
                user.removeTimer(Timer.Type.STUCK);
                HomeCommand.start(player, seconds);
            } else if (type == Timer.Type.STUCK) {
                user.removeTimer(Timer.Type.HOME);
                StuckCommand.start(player, seconds);
            } else LogoutCommand.start(player, seconds);
            user.saveAsync();
        } else if (type != null) {
            boolean pauseOnLogout = switch (type) {
                case COMBAT, CHAT, REQUEST, REPORT, LFF, ARCHERTAG -> false;
                default -> true;
            };
            Timer timer = new Timer(type, expires, pauseOnLogout && Bukkit.getPlayer(user.getUUID()) == null ? now : 0L, pauseOnLogout);
            user.addTimer(type, timer);
            if (type == Timer.Type.COMBAT) {
                user.removeTimer(Timer.Type.HOME);
                user.removeTimer(Timer.Type.STUCK);
            }
            user.saveAsync();
            if (type == Timer.Type.ARCHERTAG) Main.getInstance().refreshNameColors();
        } else {
            String name = global ? Main.getInstance().getConfig().getString("abilities.global-name", "&dAbility") : ability.getName();
            user.getAbilityTimers().put(global ? "global" : ability.getId(), new ServerTimer(name, seconds));
        }
        TimerCommand.send(sender, "added", "%player%", user.getName(), "%timer%", args[1].toLowerCase(Locale.ROOT), "%time%", C.formatChatTime(seconds));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.timer", "admin")) return List.of();
        if (args.length == 1) return Main.getInstance().getAllUsers().values().stream().map(User::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        if (args.length == 2) return TimerCommand.getTimerNames().stream()
                .filter(name -> name.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
