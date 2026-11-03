package me.Thomas.HCF.Commands.Admin.Schedule.Subcommands;

import me.Thomas.HCF.Commands.Admin.Schedule.ScheduleCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.KOTH.KOTH;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Events.EventScheduler;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ListCommand implements Subcommand {
    @Override public String getName() { return "list"; }
    @Override public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (args.length != 0) { ScheduleCommand.send(sender, "list-usage", "%alias%", alias); return true; }
        EventScheduler scheduler = Main.getInstance().getEventScheduler();
        ScheduleCommand.send(sender, "header", "%timezone%", scheduler.getTimezone().getId());
        if (scheduler.getSchedules().isEmpty()) ScheduleCommand.send(sender, "empty");
        for (EventScheduler.Schedule schedule : scheduler.getSchedules()) {
            KOTH koth = schedule.koth() == null ? null : KOTH.get(schedule.koth());
            ScheduleCommand.send(sender, "entry", "%id%", Long.toString(schedule.id()), "%type%", schedule.type().name(),
                    "%event%", koth == null ? schedule.type() == EventScheduler.Type.DTC ? "DTC" : "Conquest" : koth.getName(), "%time%", schedule.time().toString(),
                    "%duration%", Integer.toString(schedule.seconds()));
        }
        return true;
    }

    @Override public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NotNull [] args) { return List.of(); }
}
