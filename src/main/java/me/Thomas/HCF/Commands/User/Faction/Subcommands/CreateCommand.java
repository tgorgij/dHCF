package me.Thomas.HCF.Commands.User.Faction.Subcommands;

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

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CreateCommand implements Subcommand {

    @Override
    public String getName() {
        return "create";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NonNull CommandSender sender, @NotNull String @NotNull @NonNull [] args, @NotNull String alias) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        if (args.length != 1) {
            p.sendMessage(C.chat(Objects.requireNonNull(Main.getInstance().getLocaleConfig().getString("commands.faction.create.usage")).replace("%alias%", alias)));
            return true;
        }
        String name = args[0];
        User user = User.get(p);
        if (user.hasFaction()) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.already-in")));
            return true;
        }
        if (Faction.isNameInvalid(name, p)) return true;

        UUID uuid = UUID.randomUUID();
        Faction faction = new Faction(uuid, p.getUniqueId(), name);
        Main.getInstance().addFaction(faction);
        user.setFaction(uuid);
        Main.getInstance().saveTogether(user, faction);
        p.sendMessage(C.chat(Objects.requireNonNull(Main.getInstance().getLocaleConfig().getString("commands.faction.create.success")).replace("%faction%", name)));
        Bukkit.broadcast(C.chat(Objects.requireNonNull(Main.getInstance().getLocaleConfig().getString("commands.faction.create.broadcast")).replace("%player%", p.getName()).replace("%faction%", name)));
        return true;
    }
}
