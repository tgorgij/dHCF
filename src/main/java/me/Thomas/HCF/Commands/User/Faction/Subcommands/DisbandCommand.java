package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DisbandCommand implements FactionSubcommand {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "disband";
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
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.disband.usage")).replace("%alias%", alias)));
            return true;
        }
        User user = User.get(p);
        if (!user.hasFaction()) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.not-in")));
            return true;
        }
        Faction faction = Faction.get(user.getFaction());
        if (!faction.getLeader().equals(p.getUniqueId())) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.leader.not")));
            return true;
        }
        if (faction.isRaidable()) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.disband.raidable")));
            return true;
        }
        p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.disband.success"))));
        for (UUID uuid : faction.getOnlineMembers().keySet()) {
            if (uuid.equals(p.getUniqueId())) continue;
            Player player = Bukkit.getPlayer(uuid);
            Objects.requireNonNull(player).sendMessage(C.chat(Objects.requireNonNull(Objects.requireNonNull(localeConfig.getString("commands.faction.disband.broadcast")).replace("%player%", p.getName()))));
        }
        faction.disband();
        return true;
    }
}
