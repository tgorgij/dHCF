package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SetDeathbanCommand implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "setdeathban";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.faction.setdeathban", "admin")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.setdeathban.usage").replace("%alias%", alias)));
            return true;
        }
        Faction faction = Faction.get(args[0]);
        if (faction == null) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-faction")));
            return true;
        }
        if (faction.getType().equals(Faction.Type.PLAYER)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.only-system")));
            return true;
        }
        faction.setDeathban(!faction.isDeathban());
        sender.sendMessage(C.chat(localeConfig.getString("commands.faction.setdeathban.success").replace("%status%", (faction.isDeathban() ? localeConfig.getString("primary.status.enabled") : localeConfig.getString("primary.status.disabled"))).replace("%faction%", faction.getName())));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!Permission.has(sender, "command.faction.setdeathban", "admin")) {
            return List.of();
        }
        if (args.length == 1) {
            String input = args[0].toUpperCase(Locale.ROOT);

            return Main.getInstance().getAllFactions().values().stream()
                    .map(Faction::getName)
                    .filter(Objects::nonNull)
                    .filter(name -> name.toUpperCase(Locale.ROOT).startsWith(input))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        return List.of();
    }
}
