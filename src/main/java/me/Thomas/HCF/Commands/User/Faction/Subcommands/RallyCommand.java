package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public class RallyCommand implements Subcommand {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "rally";
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
        User user = User.get(player);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction == null || !faction.hasMember(player.getUniqueId())) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.not-in")));
            return true;
        }
        if (args.length > 1 || args.length == 1 && !args[0].equalsIgnoreCase("clear")) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.rally.usage"))
                    .replace("%alias%", alias)));
            return true;
        }
        String message;
        if (args.length == 1) {
            faction.setRally(null);
            message = localeConfig.getString("commands.faction.rally.cleared");
        } else {
            Location location = player.getLocation();
            faction.setRally(location);
            message = Objects.requireNonNull(localeConfig.getString("commands.faction.rally.set"))
                    .replace("%location%", C.getFormattedLocation(location))
                    .replace("%dimension%", C.getFormattedDimension(location.getWorld()));
        }
        Component broadcast = C.chat(Objects.requireNonNull(message).replace("%player%", player.getName()));
        player.sendMessage(broadcast);
        for (UUID uuid : faction.getMembers().keySet()) {
            if (uuid.equals(player.getUniqueId())) continue;
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) member.sendMessage(broadcast);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!(sender instanceof Player player) || !User.get(player).hasFaction() || args.length != 1) return List.of();
        return "clear".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("clear") : List.of();
    }
}
