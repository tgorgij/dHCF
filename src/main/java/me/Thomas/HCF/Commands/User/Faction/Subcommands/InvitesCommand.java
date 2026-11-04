package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class InvitesCommand implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "invites";
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
        if (args.length != 0) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.invites.usage").replace("%alias%", alias)));
            return true;
        }
        Set<UUID> invites = Main.getInstance().getInvitesForUser(p.getUniqueId());
        if (invites.isEmpty()) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.invites.none")));
            return true;
        }
        StringBuilder invitesString = new StringBuilder();
        for (UUID uuid : invites) {
            Faction faction = Faction.get(uuid);
            if (faction == null) {
                Main.getInstance().removeInvite(uuid, p.getUniqueId());
                continue;
            }
            invitesString.append("&b").append(faction.getName()).append("&e, ");
        }
        p.sendMessage(C.chat(localeConfig.getString("commands.faction.invites.has").replace("%invites%", invitesString.toString()).replace("%invites_count%", String.valueOf(invites.size()))));
        return true;
    }
}
