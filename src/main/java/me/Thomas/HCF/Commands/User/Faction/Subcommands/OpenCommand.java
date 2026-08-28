package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

public class OpenCommand implements Subcommand {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "open";
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
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.open.usage")).replace("%alias%", alias)));
            return true;
        }
        User user = User.get(p);
        if (!user.hasFaction()) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.not-in")));
            return true;
        }
        Faction faction = Faction.get(user.getFaction());
        if (faction.getMemberType(p.getUniqueId()).equals(Faction.MemberType.MEMBER) || faction.getMemberType(p.getUniqueId()).equals(Faction.MemberType.CAPTAIN)) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.coleader.not")));
            return true;
        }
        boolean status = faction.isOpen();
        faction.setOpen(!status);
        p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.open.success")).replace("%status%", Objects.requireNonNull(!status ? localeConfig.getString("primary.status.open") : localeConfig.getString("primary.status.closed")))));
        return true;
    }
}
