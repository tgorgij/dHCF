package me.Thomas.HCF.Commands.Admin.Timer.Subcommands;

import me.Thomas.HCF.Commands.Admin.Timer.TimerCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Ability.AbilityItems;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class RemoveCommand implements Subcommand {
    @Override
    public String getName() { return "remove"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.timer", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 2) { TimerCommand.send(sender, "remove-usage", "%alias%", alias); return true; }
        User user = User.get(args[0]);
        if (user == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        Timer.Type type = TimerCommand.getType(args[1]);
        AbilityItems.Type ability = AbilityItems.getType(args[1]);
        boolean global = args[1].equalsIgnoreCase("ability");
        if (type == null && ability == null && !global) { TimerCommand.send(sender, "invalid-timer"); return true; }
        if (type != null) {
            if (user.getTimer(type) == null) { TimerCommand.send(sender, "not-active", "%player%", user.getName(), "%timer%", args[1]); return true; }
            user.removeTimer(type);
            user.saveAsync();
            if (type == Timer.Type.ARCHERTAG) Main.getInstance().refreshNameColors();
        } else if (user.getAbilityTimers().remove(global ? "global" : ability.getId()) == null) {
            TimerCommand.send(sender, "not-active", "%player%", user.getName(), "%timer%", args[1]);
            return true;
        }
        TimerCommand.send(sender, "removed", "%player%", user.getName(), "%timer%", args[1].toLowerCase(Locale.ROOT));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.timer", "admin")) return List.of();
        if (args.length == 1) return Main.getInstance().getAllUsers().values().stream().map(User::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        if (args.length == 2) return TimerCommand.getTimerNames(false).stream()
                .filter(name -> name.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
