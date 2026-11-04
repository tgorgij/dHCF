package me.Thomas.HCF.Commands.Admin.KOTH.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.KOTH.KOTH;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;

public class StartCommand implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() { return "start"; }

    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.koth", "admin")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.koth.start.usage")
                    .replace("%alias%", alias).replace("%koth%", args.length == 0 ? "<name>" : args[0])));
            return true;
        }
        KOTH koth = KOTH.get(args[0]);
        if (koth.isRunning()) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.koth.running")));
            return true;
        }
        if (!koth.hasCaptureArea()) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.koth.no-area")));
            return true;
        }
        if (koth.getFaction() == null || Faction.get(koth.getFaction()) == null) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.koth.no-faction")));
            return true;
        }
        long seconds;
        try {
            seconds = Long.parseLong(args[1]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.koth.invalid-time")));
            return true;
        }
        if (seconds <= 0 || seconds > Integer.MAX_VALUE) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.koth.invalid-time")));
            return true;
        }
        koth.start(seconds);
        sender.sendMessage(C.chat(localeConfig.getString("commands.koth.start.success")
                .replace("%koth%", koth.getName())));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!Permission.has(sender, "command.koth", "admin") || args.length != 2) return List.of();
        return List.of("60", "300", "600", "900").stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
    }
}
