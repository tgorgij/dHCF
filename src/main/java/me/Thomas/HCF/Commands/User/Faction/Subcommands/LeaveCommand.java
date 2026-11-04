package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.UUID;

public class LeaveCommand implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "leave";
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
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.leave.usage").replace("%alias%", alias)));
            return true;
        }
        User user = User.get(p);
        if (!user.hasFaction()) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.not-in")));
            return true;
        }
        Faction faction = Faction.get(user.getFaction());
        long freezeRemaining = faction.getDTRFreezeExpiry() - System.currentTimeMillis();
        if (freezeRemaining > 0) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.leave.dtr-freeze")
                    .replace("%time%", C.formatChatTime((freezeRemaining + 999L) / 1000L))));
            return true;
        }
        if (faction.getLeader().equals(p.getUniqueId())) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.leave.leader").replace("%alias%", alias)));
            return true;
        }
        if (faction.isRaidable()) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.leave.raidable")));
            return true;
        }
        p.sendMessage(C.chat(localeConfig.getString("commands.faction.leave.success")));
        user.setFaction(null);
        faction.removeMember(p.getUniqueId());
        for (UUID uuid : faction.getOnlineMembers().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            player.sendMessage(C.chat(localeConfig.getString("commands.faction.leave.broadcast").replace("%player%", p.getName())));
        }
        Main.getInstance().saveTogether(user, faction);
        return true;
    }
}
