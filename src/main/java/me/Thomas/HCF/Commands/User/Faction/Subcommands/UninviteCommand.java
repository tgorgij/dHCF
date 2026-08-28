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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class UninviteCommand implements Subcommand {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "uninvite";
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
        if (args.length != 1) {
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.uninvite.usage")).replace("%alias%", alias)));
            return true;
        }
        String name = args[0];
        User target = User.get(name);
        if (target == null) {
            p.sendMessage(C.chat(localeConfig.getString("primary.no-player")));
            return true;
        }
        if (!Main.getInstance().hasInvite(faction.getUUID(), target.getUUID())) {
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.uninvite.not-invited")).replace("%player%", target.getName())));
            return true;
        }
        Main.getInstance().removeInvite(faction.getUUID(), target.getUUID());
        p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.uninvite.success")).replace("%player%", target.getName())));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!(sender instanceof Player p)) {
            return List.of();
        }

        if (args.length == 1) {
            String input = args[0].toUpperCase();
            User user = User.get(p);
            if (!user.hasFaction()) return List.of();
            ArrayList<String> list = new ArrayList<>();
            for (UUID uuid : Main.getInstance().getInvitesForFaction(user.getFaction())) {
                User target = User.get(uuid);
                list.add(target.getName());
            }

            return list.stream()
                    .filter(name -> name.toUpperCase().startsWith(input))
                    .toList();
        }
        return List.of();

    }
}
