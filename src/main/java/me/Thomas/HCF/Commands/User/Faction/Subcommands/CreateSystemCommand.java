package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.UUID;

public class CreateSystemCommand implements Subcommand {

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
        if (!Permission.has(sender, "command.faction.createsystem")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.createsystem.usage").replace("%alias%", alias)));
            return true;
        }
        String name = args[0];
        if (Faction.isNameInvalid(name, sender)) return true;

        UUID uuid = UUID.randomUUID();
        Faction faction = new Faction(uuid, name);
        Main.getInstance().addFaction(faction);
        faction.saveAsync();
        sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.createsystem.success").replace("%faction%", name)));
        Bukkit.broadcast(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.createsystem.broadcast").replace("%player%", sender.getName()).replace("%faction%", name)));
        return true;
    }
}
