package me.Thomas.HCF.Commands.Admin.KOTH.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.KOTH.KOTH;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SetFactionCommand implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() { return "setfaction"; }

    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.koth", "admin")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.koth.setfaction.usage")
                    .replace("%alias%", alias).replace("%koth%", args.length == 0 ? "<name>" : args[0])));
            return true;
        }
        KOTH koth = KOTH.get(args[0]);
        Faction faction = args[1].equalsIgnoreCase("none") ? null : Faction.get(args[1]);
        if (faction == null && !args[1].equalsIgnoreCase("none")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-faction")));
            return true;
        }
        koth.setFaction(faction == null ? null : faction.getUUID());
        koth.saveAsync();
        sender.sendMessage(C.chat(localeConfig.getString("commands.koth.setfaction.success")
                .replace("%koth%", koth.getName())));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!Permission.has(sender, "command.koth", "admin") || args.length != 2) return List.of();
        List<String> names = new ArrayList<>();
        names.add("none");
        for (Faction faction : Main.getInstance().getAllFactions().values()) names.add(faction.getName());
        return names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
