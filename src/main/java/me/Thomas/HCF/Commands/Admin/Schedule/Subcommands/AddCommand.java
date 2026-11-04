package me.Thomas.HCF.Commands.Admin.Schedule.Subcommands;

import me.Thomas.HCF.Commands.Admin.Schedule.ScheduleCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.KOTH.KOTH;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
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
        if (!(koth && args.length == 4 || args.length == 2 && (args[0].equalsIgnoreCase("conquest") || args[0].equalsIgnoreCase("dtc")))) {
            ScheduleCommand.send(sender, "add-usage", "%alias%", alias);
            return true;
        }
        String feature = args[0].toLowerCase(Locale.ROOT);
        if (!Main.getInstance().isFeatureEnabled(feature)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.feature-disabled")));
            return true;
        }
        LocalTime time;
        int seconds = 0;
        try {
            String value = args[koth ? 2 : 1];
            time = ScheduleCommand.parseTime(value);
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
        EventScheduler scheduler = Main.getInstance().getEventScheduler();
        EventScheduler.Schedule schedule = scheduler.add(
                EventScheduler.Type.valueOf(args[0].toUpperCase(Locale.ROOT)), event == null ? null : event.getUUID(), time, seconds);
        ScheduleCommand.send(sender, "added", "%id%", Integer.toString(scheduler.getSchedules().indexOf(schedule) + 1), "%event%", event == null ? args[0].equalsIgnoreCase("dtc") ? "DTC" : "Conquest" : event.getName(),
                "%time%", ScheduleCommand.formatTime(time), "%timezone%", Main.getInstance().getEventScheduler().getTimezone().getId());
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        String input = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) return List.of("koth", "conquest", "dtc").stream().filter(value -> value.startsWith(input)).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("koth")) return Main.getInstance().getAllKOTHs().values().stream()
                .map(KOTH::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input)).sorted().toList();
        if (args.length == 2 && (args[0].equalsIgnoreCase("conquest") || args[0].equalsIgnoreCase("dtc")) || args.length == 3 && args[0].equalsIgnoreCase("koth")) {
            return List.of("12:00pm", "6:00pm", "8:00pm").stream().filter(value -> value.startsWith(input)).toList();
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("koth")) return List.of("300", "600", "900").stream().filter(value -> value.startsWith(input)).toList();
        return List.of();
    }
}
