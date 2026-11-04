package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.UUID;

public final class FriendlyFireCommand implements Subcommand {
    @Override public String getName() { return "ff"; }
    @Override public List<String> getAliases() { return List.of("friendlyfire"); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        LocaleConfig locale = Main.getInstance().getLocaleConfig();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C.chat(locale.getString("primary.not-player")));
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(C.chat(locale.getString("commands.faction.ff.usage")
                    .replace("%alias%", alias)));
            return true;
        }
        User user = User.get(player);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction == null || !faction.hasMember(player.getUniqueId())) {
            sender.sendMessage(C.chat(locale.getString("commands.faction.not-in")));
            return true;
        }
        boolean enabled = Main.getInstance().toggleFriendlyFire(faction.getUUID());
        Component message = C.chat(locale.getString("commands.faction.ff.success")
                .replace("%player%", player.getName())
                .replace("%status%", locale.getString(
                        "primary.status." + (enabled ? "enabled" : "disabled"))));
        player.sendMessage(message);
        for (UUID uuid : faction.getMembers().keySet()) {
            if (uuid.equals(player.getUniqueId())) continue;
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) member.sendMessage(message);
        }
        return true;
    }
}
