package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

public final class FocusCommand implements Subcommand {
    @Override public String getName() { return "focus"; }
    @Override public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        FileConfiguration locale = Main.getInstance().getLocaleConfig();
        if (!(sender instanceof Player player)) {
            sender.sendMessage(C.chat(locale.getString("primary.not-player")));
            return true;
        }
        User user = User.get(player);
        Faction own = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (own == null || !own.hasMember(player.getUniqueId())) {
            sender.sendMessage(C.chat(locale.getString("commands.faction.not-in")));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(C.chat(Objects.requireNonNull(locale.getString("commands.faction.focus.usage"))
                    .replace("%alias%", alias)));
            return true;
        }
        Faction faction = Faction.get(args[0]);
        User target = faction == null ? User.get(args[0]) : null;
        if (faction == null && target == null) {
            sender.sendMessage(C.chat(locale.getString("commands.faction.focus.not-found")));
            return true;
        }
        if (faction != null && faction.getType() != Faction.Type.PLAYER) {
            sender.sendMessage(C.chat(locale.getString("primary.only-player-faction")));
            return true;
        }
        if ((faction != null && faction.getUUID().equals(own.getUUID()))
                || (target != null && (own.hasMember(target.getUUID()) || own.getUUID().equals(target.getFaction())))) {
            sender.sendMessage(C.chat(locale.getString("commands.faction.focus.own")));
            return true;
        }
        Main.Focus focus = new Main.Focus(faction != null ? faction.getUUID() : target.getUUID(), faction != null);
        if (focus.equals(Main.getInstance().getFocus(own.getUUID()))) {
            Main.getInstance().setFocus(own.getUUID(), null);
            broadcast(own, player, locale.getString("commands.faction.focus.cleared"));
        } else {
            Main.getInstance().setFocus(own.getUUID(), focus);
            broadcast(own, player, Objects.requireNonNull(locale.getString("commands.faction.focus.success"))
                    .replace("%target%", faction != null ? faction.getName() : target.getName()));
        }
        return true;
    }

    private void broadcast(Faction faction, Player player, String format) {
        Component message = C.chat(Objects.requireNonNull(format).replace("%player%", player.getName()));
        player.sendMessage(message);
        for (UUID uuid : faction.getMembers().keySet()) {
            if (uuid.equals(player.getUniqueId())) continue;
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) member.sendMessage(message);
        }
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        User user = User.get(player);
        if (!user.hasFaction()) return List.of();
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Faction faction : Main.getInstance().getAllFactions().values()) {
            if (faction.getType() == Faction.Type.PLAYER && !faction.getUUID().equals(user.getFaction())) names.add(faction.getName());
        }
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!user.getFaction().equals(User.get(target).getFaction())) names.add(target.getName());
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
