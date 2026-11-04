package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class DebugCommand implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "debug";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {

        if (!Permission.has(sender, "command.faction.debug")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }

        if (args.length != 0) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.debug.usage").replace("%alias%", alias)));
            return true;
        }

        boolean debug = Main.getInstance().isFactionDebug();
        Main.getInstance().setFactionDebug(!debug);
        sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.debug.toggle").replace("%status%", (!debug ? localeConfig.getString("primary.status.enabled") : localeConfig.getString("primary.status.disabled")))));
        return true;
    }
}
