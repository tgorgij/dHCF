package me.Thomas.HCF.Commands.Admin.KOTH.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.KOTH.KOTH;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;

public class SetPearlableCommand implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() { return "setpearlable"; }

    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.koth", "admin")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.koth.setpearlable.usage")
                    .replace("%alias%", alias).replace("%koth%", args.length == 0 ? "<name>" : args[0])));
            return true;
        }
        KOTH koth = KOTH.get(args[0]);
        if (koth.getFaction() == null) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.koth.requires-faction")));
            return true;
        }
        if (!args[1].equalsIgnoreCase("true") && !args[1].equalsIgnoreCase("false")) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.koth.invalid-state")));
            return true;
        }
        koth.setPearlable(Boolean.parseBoolean(args[1]));
        koth.saveAsync();
        sender.sendMessage(C.chat(localeConfig.getString("commands.koth.setpearlable.success")
                .replace("%koth%", koth.getName())));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!Permission.has(sender, "command.koth", "admin") || args.length != 2) return List.of();
        return List.of("true", "false").stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
    }
}
