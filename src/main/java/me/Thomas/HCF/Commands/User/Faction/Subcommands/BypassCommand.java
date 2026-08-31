package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

public class BypassCommand implements Subcommand {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "bypass";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
            return true;
        }
        if (!Permission.has(p, "faction.bypass", "admin")) {
            p.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 0) {
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.bypass.usage")).replace("%alias%", alias)));
            return true;
        }
        User user = User.get(p);
        user.setBypass(!user.isBypass());
        p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.bypass.toggle")).replace("%status%", Objects.requireNonNull(user.isBypass() ? localeConfig.getString("primary.status.enabled") : localeConfig.getString("primary.status.disabled")))));
        return false;
    }
}
