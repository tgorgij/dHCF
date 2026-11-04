package me.Thomas.HCF.Commands.Admin.KOTH.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.KOTH.KOTH;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class SetPos2Command implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() { return "setpos2"; }

    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.koth", "admin")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.koth.setpos2.usage")
                    .replace("%alias%", alias).replace("%koth%", args.length == 0 ? "<name>" : args[0])));
            return true;
        }
        KOTH koth = KOTH.get(args[0]);
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
            return true;
        }
        if (koth.isRunning()) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.koth.cancel-before-edit")));
            return true;
        }
        try {
            koth.setPos2(p.getLocation());
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.koth.different-world")));
            return true;
        }
        koth.saveAsync();
        sender.sendMessage(C.chat(localeConfig.getString("commands.koth.setpos2.success")
                .replace("%koth%", koth.getName())));
        return true;
    }
}
