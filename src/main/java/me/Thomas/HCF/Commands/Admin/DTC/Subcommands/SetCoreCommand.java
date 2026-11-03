package me.Thomas.HCF.Commands.Admin.DTC.Subcommands;

import me.Thomas.HCF.Commands.Admin.DTC.DTCCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.DTC.DTC;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import org.bukkit.entity.Player;
import org.bukkit.block.Block;

import java.util.List;

public class SetCoreCommand implements Subcommand {
    @Override public String getName() { return "setcore"; }
    @Override public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.dtc", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        if (args.length != 0) { DTCCommand.send(sender, "setcore-usage", "%alias%", alias); return true; }
        Block block = p.getTargetBlockExact(6);
        if (block == null) throw new IllegalArgumentException("invalid-core");
        DTC.get().setCore(block);
        DTC.get().saveAsync();
        DTCCommand.send(sender, "core-set");
        return true;
    }
}
