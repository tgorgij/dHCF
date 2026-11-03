package me.Thomas.HCF.Commands.Donator.Reclaim.Subcommands;

import me.Thomas.HCF.Commands.Donator.Reclaim.ReclaimCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Database.SaveSnapshot;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Level;

public class ResetCommand implements Subcommand {
    @Override public String getName() { return "reset"; }
    @Override public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.reclaim.reset", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 0) { ReclaimCommand.send(sender, "reset-usage", "%alias%", alias); return true; }
        try {
            Main.getInstance().getDatabaseManager().save(List.of(new SaveSnapshot("UPDATE players SET reclaimed=0", () -> {}))).join();
            for (User user : Main.getInstance().getAllUsers().values()) user.setReclaimed(false);
            ReclaimCommand.send(sender, "reset");
        } catch (RuntimeException exception) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not reset reclaims", exception);
            ReclaimCommand.send(sender, "reset-failed");
        }
        return true;
    }
}
