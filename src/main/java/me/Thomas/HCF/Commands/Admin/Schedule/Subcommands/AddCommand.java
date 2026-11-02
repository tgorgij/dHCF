package me.Thomas.HCF.Commands.Admin.Schedule.Subcommands;

import me.Thomas.HCF.Commands.Admin.Schedule.ScheduleCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.KOTH.KOTH;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Events.EventScheduler;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

public class AddCommand implements Subcommand {

    @Override public String getName() { return "add"; }
    @Override public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        boolean koth = args.length > 0 && args[0].equalsIgnoreCase("koth");
        if (!(koth && args.length == 4 || args.length == 2 && args[0].equalsIgnoreCase("conquest"))) {
            ScheduleCommand.send(sender, "add-usage", "%alias%", alias);
            return true;
        }
        LocalTime time;
        int seconds = 0;
        try {
            String value = args[koth ? 2 : 1];
            if (!value.matches("[0-9]{2}:[0-9]{2}")) throw new IllegalArgumentException();
            time = LocalTime.parse(value);
            if (koth) {
                seconds = Integer.parseInt(args[3]);
                if (seconds <= 0) throw new IllegalArgumentException();
            }
        } catch (DateTimeException | IllegalArgumentException exception) {
            ScheduleCommand.send(sender, "invalid");
            return true;
        }
        KOTH event = koth ? KOTH.get(args[1]) : null;
        if (koth && event == null) {
            ScheduleCommand.send(sender, "no-koth");
            return true;
        }
        EventScheduler.Schedule schedule = Main.getInstance().getEventScheduler().add(
                koth ? EventScheduler.Type.KOTH : EventScheduler.Type.CONQUEST, event == null ? null : event.getUUID(), time, seconds);
        ScheduleCommand.send(sender, "added", "%id%", Long.toString(schedule.id()), "%event%", event == null ? "Conquest" : event.getName(),
                "%time%", time.toString(), "%timezone%", Main.getInstance().getEventScheduler().getTimezone().getId());
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        String input = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) return List.of("koth", "conquest").stream().filter(value -> value.startsWith(input)).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("koth")) return Main.getInstance().getAllKOTHs().values().stream()
                .map(KOTH::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input)).sorted().toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("conquest") || args.length == 3 && args[0].equalsIgnoreCase("koth")) {
            return List.of("12:00", "18:00", "20:00").stream().filter(value -> value.startsWith(input)).toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("koth")) return List.of("300", "600", "900").stream().filter(value -> value.startsWith(input)).toList();
        return List.of();
    }
}
