package me.Thomas.HCF.Commands.Admin.Mountain.Subcommands;

import me.Thomas.HCF.Commands.Admin.Mountain.MountainCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Mountain.Mountain;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.Database.SaveSnapshot;
import me.Thomas.HCF.Utils.Names.Names;
import java.util.ArrayList;
import java.util.UUID;

public class CreateCommand implements Subcommand {

    @Override
    public String getName() { return "create"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.mountain", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) {
            MountainCommand.send(sender, "create-usage", "%alias%", alias, "%mountain%", args.length == 0 ? "<name>" : args[0]);
            return true;
        }
        if (Mountain.get(args[0]) != null) { MountainCommand.send(sender, "already-exists"); return true; }
        Faction faction = Faction.get(args[0]);
        if (faction != null && faction.getType() != Faction.Type.MOUNTAIN) {
            MountainCommand.send(sender, "faction-exists");
            return true;
        }
        if (Names.isInvalid(args[0], sender)) return true;
        boolean created = faction == null;
        if (created) {
            faction = new Faction(UUID.randomUUID(), args[0]);
            faction.setType(Faction.Type.MOUNTAIN);
        }
        Mountain mountain = new Mountain(faction.getUUID());
        List<SaveSnapshot> snapshots = new ArrayList<>();
        SaveSnapshot factionSnapshot = faction.snapshot();
        if (factionSnapshot != null) snapshots.add(factionSnapshot);
        snapshots.add(mountain.snapshot());
        Main.getInstance().getDatabaseManager().save(snapshots).join();
        if (created) Main.getInstance().addFaction(faction);
        Main.getInstance().addMountain(mountain);
        MountainCommand.send(sender, "created", "%mountain%", mountain.getName());
        return true;
    }
}
