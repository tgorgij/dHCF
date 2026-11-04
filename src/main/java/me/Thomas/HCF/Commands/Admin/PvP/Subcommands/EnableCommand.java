package me.Thomas.HCF.Commands.Admin.PvP.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class EnableCommand implements Subcommand {
    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();
    @Override
    public String getName() {
        return "enable";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.pvp.enable.usage")
                    .replace("%alias%", alias)));
            return true;
        }
        User user = User.get(player);
        if (!user.hasPvpProtection()) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.pvp.enable.none")));
            return true;
        }
        user.removeTimer(Timer.Type.PVP);
        user.removeTimer(Timer.Type.STARTING);
        user.saveAsync();
        sender.sendMessage(C.chat(localeConfig.getString("commands.pvp.enable.success")));
        return true;
    }
}
