package me.Thomas.HCF.Commands.Donator;

import me.Thomas.HCF.Events.Player.Item.UserBottleEvent;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.logging.Level;

public class BottleCommand implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.bottle", "donator")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        if (args.length != 0) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.bottle.usage").replace("%alias%", alias)));
            return true;
        }
        if (p.isDead() || User.get(p).isInStaffMode()) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.bottle.unavailable")));
            return true;
        }
        int experience = p.calculateTotalExperiencePoints();
        if (experience <= 0) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.bottle.no-experience")));
            return true;
        }
        ItemStack[] previous = p.getInventory().getStorageContents();
        for (int i = 0; i < previous.length; i++) {
            if (previous[i] != null) previous[i] = previous[i].clone();
        }
        try {
            if (!p.getInventory().addItem(UserBottleEvent.createBottle(experience)).isEmpty()) {
                p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.bottle.no-space")));
                return true;
            }
            p.setExperienceLevelAndProgress(0);
            p.setTotalExperience(0);
            p.saveData();
        } catch (RuntimeException exception) {
            p.getInventory().setStorageContents(previous);
            p.setExperienceLevelAndProgress(experience);
            p.setTotalExperience(experience);
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not bottle experience for " + p.getUniqueId(), exception);
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.bottle.failed")));
            return true;
        }
        p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.bottle.success")
                .replace("%experience%", Integer.toString(experience))));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        return List.of();
    }
}
