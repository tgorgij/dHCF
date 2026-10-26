package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class ForceDisband implements Subcommand {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "forcedisband";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.faction.forcedisband")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.forcedisband.usage")).replace("%alias%", alias)));
            return true;
        }
        Faction faction = Faction.get(args[0]);
        if (faction == null) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("primary.no-faction"))));
            return true;
        }
        if (faction.getType() == Faction.Type.PLAYER && faction.isRaidable()) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.forcedisband.raidable")));
            return true;
        }
        if (!faction.disband()) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.save-failed", "&cCould not save changes. Please try again.")));
            return true;
        }
        sender.sendMessage(C.chat(Objects.requireNonNull(Objects.requireNonNull(localeConfig.getString("commands.faction.forcedisband.success")).replace("%faction%", faction.getName()))));
        for (UUID uuid : faction.getOnlineMembers().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            Objects.requireNonNull(player).sendMessage(C.chat(Objects.requireNonNull(Objects.requireNonNull(localeConfig.getString("commands.faction.forcedisband.broadcast")).replace("%player%", sender.getName()))));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!Permission.has(sender, "command.faction.forcedisband")) {
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
