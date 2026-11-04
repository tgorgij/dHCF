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
import java.util.*;

public final class UnclaimCommand implements Subcommand {
    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();
    @Override public String getName() { return "unclaim"; }
    @Override public List<String> getAliases() { return List.of(); }
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
        Faction.MemberType actor = faction.getMemberType(player.getUniqueId());
        if (actor != Faction.MemberType.LEADER && actor != Faction.MemberType.COLEADER) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.coleader.not")));
            return true;
        }
        if (args.length != 0) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.unclaim.usage").replace("%alias%", alias)));
            return true;
        }
        me.Thomas.HCF.IO.Claims.Claim claim = Main.getInstance().getClaimAt(player.getLocation());
        if (claim == null || !claim.faction().equals(faction.getUUID())) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.unclaim.not-claim")));
            return true;
        }
        if (!Main.getInstance().removeClaim(claim)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.save-failed", "&cCould not save changes. Please try again.")));
            return true;
        }
        sender.sendMessage(C.chat(localeConfig.getString("commands.faction.unclaim.success")));
        return true;
    }
}
