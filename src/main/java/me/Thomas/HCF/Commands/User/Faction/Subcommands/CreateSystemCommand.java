package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CreateSystemCommand implements FactionSubcommand {

    @Override
    public String getName() {
        return "createsystem";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NonNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        if (!p.hasPermission("hcf.command.faction.createsystem")) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }

        if (args.length != 1) {
            p.sendMessage(C.chat(Objects.requireNonNull(Main.getInstance().getLocaleConfig().getString("commands.faction.createsystem.usage")).replace("%alias%", alias)));
            return true;
        }
        String name = args[0];
        if (Main.getInstance().getFaction(name) != null) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.create.already-exists")));
            return true;
        }
        if (Main.getInstance().getConfig().getStringList("factions.name.blocked").stream()
                .anyMatch(name::equalsIgnoreCase)) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.create.blocked")));
            return true;
        }
        if (name.length() < Main.getInstance().getConfig().getInt("factions.name.min") || name.length() > Main.getInstance().getConfig().getInt("factions.name.max")) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.create.invalid-length")));
            return true;
        }

        UUID uuid = UUID.randomUUID();
        Faction faction = new Faction(uuid, name);
        Main.getInstance().addFaction(faction);
        faction.save();
        p.sendMessage(C.chat(Objects.requireNonNull(Main.getInstance().getLocaleConfig().getString("commands.faction.createsystem.success")).replace("%faction%", name)));
        Bukkit.broadcast(C.chat(Objects.requireNonNull(Main.getInstance().getLocaleConfig().getString("commands.faction.createsystem.broadcast")).replace("%player%", p.getName()).replace("%faction%", name)));
        return true;
    }
}
