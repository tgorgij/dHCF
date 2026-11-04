package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Claims.Claiming;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class MapCommand implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "map";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(
            @NotNull CommandSender sender,
            @NotNull String @NonNull [] args,
            @NotNull String alias
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
            return true;
        }

        if (args.length != 0) {
            player.sendMessage(C.chat(localeConfig.getString("commands.faction.map.usage").replace("%alias%", alias)));
            return true;
        }

        if (Claiming.hasVisiblePillars(player)) {
            Claiming.hidePillars(player);
            player.sendMessage(C.chat(localeConfig.getString("commands.faction.map.hidden")));
            return true;
        }

        if (!Claiming.showNearbyClaims(player)) {
            player.sendMessage(C.chat(localeConfig.getString("commands.faction.map.none")));
        }
        return true;
    }
}
