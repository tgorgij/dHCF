package me.Thomas.HCF.Commands.Admin.Schedule.Subcommands;

import me.Thomas.HCF.Commands.Admin.Schedule.ScheduleCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Events.EventScheduler;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RemoveCommand implements Subcommand {
    @Override public String getName() { return "remove"; }
    @Override public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (args.length != 1) { ScheduleCommand.send(sender, "remove-usage", "%alias%", alias); return true; }
        int index;
        try { index = Integer.parseInt(args[0]); }
        catch (NumberFormatException exception) { ScheduleCommand.send(sender, "not-found"); return true; }
        EventScheduler scheduler = Main.getInstance().getEventScheduler();
        List<EventScheduler.Schedule> schedules = scheduler.getSchedules();
        if (index < 1 || index > schedules.size()) { ScheduleCommand.send(sender, "not-found"); return true; }
        ScheduleCommand.send(sender, scheduler.remove(schedules.get(index - 1).id()) ? "removed" : "not-found", "%id%", Integer.toString(index));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        if (args.length != 1) return List.of();
        return java.util.stream.IntStream.rangeClosed(1, Main.getInstance().getEventScheduler().getSchedules().size())
                .mapToObj(Integer::toString).filter(index -> index.startsWith(args[0])).toList();
    }
}
