package me.Thomas.HCF.Commands.Staff;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;

public class RestoreCommand implements TabExecutor {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.restore", "staff")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.restore.usage"))
                    .replace("%alias%", alias)));
            return true;
        }
        Player p = Bukkit.getPlayerExact(args[0]);
        if (p == null || !p.isOnline()) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.restore.offline"))
                    .replace("%player%", args[0])));
            return true;
        }
        if (User.get(p).isInStaffMode()) {
            Main.getInstance().getStaffMode().message(sender, "invsee.target-in-mode");
            return true;
        }
        if (p.isDead()) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.restore.dead"))
                    .replace("%player%", p.getName())));
            return true;
        }
        User user = User.get(p);
        byte[] inventory = user.getLastInventory();
        Integer experience = user.getLastExperience();
        if (inventory == null || experience == null) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.restore.no-inventory"))
                    .replace("%player%", p.getName())));
            return true;
        }
        ItemStack[] contents;
        try {
            contents = ItemStack.deserializeItemsFromBytes(inventory);
            if (contents.length != p.getInventory().getContents().length || experience < 0) {
                throw new IllegalArgumentException("Invalid saved death state");
            }
        } catch (RuntimeException exception) {
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not read death state for " + p.getUniqueId(), exception);
            sender.sendMessage(C.chat(localeConfig.getString("commands.restore.failed")));
            return true;
        }
        ItemStack[] previousInventory = p.getInventory().getContents();
        int previousExperience = p.calculateTotalExperiencePoints();
        try {
            p.getInventory().setContents(contents);
            p.setExperienceLevelAndProgress(experience);
            p.setTotalExperience(experience);
            p.saveData();
            user.setLastInventory(null);
            user.setLastExperience(null);
            user.save();
        } catch (RuntimeException exception) {
            user.setLastInventory(inventory);
            user.setLastExperience(experience);
            p.getInventory().setContents(previousInventory);
            p.setExperienceLevelAndProgress(previousExperience);
            p.setTotalExperience(previousExperience);
            p.saveData();
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not restore death state for " + p.getUniqueId(), exception);
            sender.sendMessage(C.chat(localeConfig.getString("commands.restore.failed")));
            return true;
        }
        sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.restore.success"))
                .replace("%player%", p.getName())));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.restore", "staff") || args.length != 1) return List.of();
        String input = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
