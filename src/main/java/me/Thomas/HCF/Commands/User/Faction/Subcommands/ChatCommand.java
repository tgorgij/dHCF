package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Events.Player.Connection.UserChatEvent;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import java.util.List;
import java.util.Locale;

public final class ChatCommand implements Subcommand {
    @Override public String getName() { return "chat"; }
    @Override public List<String> getAliases() { return List.of("c"); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        LocaleConfig locale = Main.getInstance().getLocaleConfig();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C.chat(locale.getString("primary.not-player")));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(C.chat(locale.getString("commands.faction.chat.usage").replace("%alias%", alias)));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("p")) {
            UserChatEvent.setStaffChat(player.getUniqueId(), false);
            UserChatEvent.setAllyChat(player.getUniqueId(), false);
            UserChatEvent.setFactionChat(player.getUniqueId(), false);
            sender.sendMessage(C.chat(locale.getString("commands.faction.chat.public")));
            return true;
        }
        User user = User.get(player);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction == null || !faction.hasMember(player.getUniqueId())) {
            sender.sendMessage(C.chat(locale.getString("commands.faction.not-in")));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("f")) {
            UserChatEvent.setFactionChat(player.getUniqueId(), true);
            sender.sendMessage(C.chat(locale.getString("commands.faction.chat.faction")));
        } else if (args.length == 1 && args[0].equalsIgnoreCase("a")) {
            UserChatEvent.setAllyChat(player.getUniqueId(), true);
            sender.sendMessage(C.chat(locale.getString("commands.faction.chat.ally")));
        } else {
            UserChatEvent.sendFactionMessage(player, String.join(" ", args));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!(sender instanceof Player) || args.length != 1) return List.of();
        return List.of("p", "f", "a").stream().filter(mode -> mode.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
