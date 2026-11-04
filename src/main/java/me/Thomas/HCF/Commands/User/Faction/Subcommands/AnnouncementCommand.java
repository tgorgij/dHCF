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
import java.util.*;

public final class AnnouncementCommand implements Subcommand {
    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();
    @Override public String getName() { return "announcement"; }
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
        if (faction.getMemberType(player.getUniqueId()).getValue() < Faction.MemberType.CAPTAIN.getValue()) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.captain.not")));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.announcement.usage").replace("%alias%", alias)));
            return true;
        }
        String message = stripColors(String.join(" ", args));
        faction.setAnnouncement(message);
        faction.saveAsync();
        for (UUID uuid : faction.getOnlineMembers().keySet()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) member.sendMessage(C.chat(localeConfig.getString("commands.faction.announcement.success")
                    .replace("%player%", player.getName()))
                    .replaceText(builder -> builder.matchLiteral("%announcement%").replacement(net.kyori.adventure.text.Component.text(message))));
        }
        return true;
    }

    public static String stripColors(String message) {
        return message.replaceAll("(?i)[&§]#[0-9a-f]{6}", "").replaceAll("(?i)[&§][0-9a-fk-orx]", "");
    }
}
