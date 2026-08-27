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

public class InviteCommand implements FactionSubcommand {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "invite";
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
        if (faction.getMemberType(p.getUniqueId()).equals(Faction.MemberType.MEMBER)) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.captain.not")));
            return true;
        }
        if (args.length != 1) {
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.invite.usage")).replace("%alias%", alias)));
            return true;
        }
        String name = args[0];
        if (name.equalsIgnoreCase(p.getName())) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.invite.self")));
            return true;
        }
        User target = User.get(name);
        if (target == null) {
            p.sendMessage(C.chat(localeConfig.getString("primary.no-player")));
            return true;
        }
        if (target.hasFaction() && target.getFaction().equals(user.getFaction())) {
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.invite.your-faction")).replace("%player%", target.getName())));
            return true;
        }
        if (target.hasFaction()) {
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.invite.other-faction")).replace("%player%", target.getName())));
            return true;
        }
        if (Main.getInstance().hasInvite(faction.getUUID(), target.getUUID())) {
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.invite.pending")).replace("%player%", target.getName())));
            return true;
        }
        if (faction.isOpen()) {
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.invite.open"))));
            return true;
        }
        Main.getInstance().addInvite(faction.getUUID(), target.getUUID());
        p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.invite.success")).replace("%player%", target.getName())));
        Player targetPlayer = Bukkit.getPlayer(target.getUUID());
        if (targetPlayer != null) {
            targetPlayer.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.invite.other")).replace("%faction%", faction.getName()).replace("%player%", p.getName())));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (args.length == 1) {
            String input = args[0].toUpperCase();

            return Main.getInstance().getAllUsers().values().stream()
                    .map(User::getName)
                    .filter(name -> name.toUpperCase().startsWith(input))
                    .toList();
        }

        return List.of();
    }
}
