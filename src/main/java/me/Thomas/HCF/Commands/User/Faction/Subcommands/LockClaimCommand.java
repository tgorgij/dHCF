package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public class LockClaimCommand implements Subcommand {
    @Override
    public String getName() { return "lockclaim"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        if (args.length != 0) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.lockclaim.usage").replace("%alias%", alias)));
            return true;
        }
        if (!Main.getInstance().isSOTWOngoing()) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.lockclaim.not-sotw")));
            return true;
        }
        User user = User.get(p);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction == null || !faction.hasMember(p.getUniqueId())) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.not-in")));
            return true;
        }
        if (faction.getMemberType(p.getUniqueId()).getValue() < Faction.MemberType.COLEADER.getValue()) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.coleader.not")));
            return true;
        }
        boolean locked = Main.getInstance().toggleClaimLock(faction.getUUID());
        Component message = C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.lockclaim.success")
                .replace("%player%", p.getName()).replace("%status%", Main.getInstance().getLocaleConfig()
                        .getString("primary.status." + (locked ? "enabled" : "disabled"))));
        for (UUID uuid : faction.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) member.sendMessage(message);
        }
        return true;
    }
}
