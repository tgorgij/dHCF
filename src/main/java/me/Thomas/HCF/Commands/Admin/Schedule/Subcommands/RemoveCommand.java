package me.Thomas.HCF.Commands.Admin.Schedule.Subcommands;

import me.Thomas.HCF.Commands.Admin.Schedule.ScheduleCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RemoveCommand implements Subcommand {
    @Override public String getName() { return "remove"; }
    @Override public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (args.length != 1) { ScheduleCommand.send(sender, "remove-usage", "%alias%", alias); return true; }
        long id;
        try { id = Long.parseLong(args[0]); }
        catch (NumberFormatException exception) { ScheduleCommand.send(sender, "not-found"); return true; }
        ScheduleCommand.send(sender, Main.getInstance().getEventScheduler().remove(id) ? "removed" : "not-found", "%id%", Long.toString(id));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        if (args.length != 1) return List.of();
        return Main.getInstance().getEventScheduler().getSchedules().stream().map(schedule -> Long.toString(schedule.id()))
                .filter(id -> id.startsWith(args[0])).toList();
    }
}
