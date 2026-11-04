package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.UUID;

public class RenameCommand implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "rename";
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
        if (args.length != 1) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.rename.usage").replace("%alias%", alias)));
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
        String name = args[0];
        if (Faction.isNameInvalid(name, p)) return true;
        Main.getInstance().renameFaction(faction.getUUID(), name);
        faction.setName(name);
        faction.saveAsync();
        p.sendMessage(C.chat(localeConfig.getString("commands.faction.rename.success").replace("%faction%", name)));
        for (UUID uuid : faction.getOnlineMembers().keySet()) {
            Player player = Main.getInstance().getServer().getPlayer(uuid);
            if (player == null) continue;
            player.sendMessage(C.chat(localeConfig.getString("commands.faction.rename.broadcast").replace("%player%", p.getName()).replace("%faction%", name)));
        }
        return true;
    }
}
