package me.Thomas.HCF.Utils.Permission;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class Permission {

    public static boolean has(Player p, String permission, String type) {
        return p.hasPermission("*") || p.hasPermission("hcf.*") || p.hasPermission("hcf." + type) || p.hasPermission("hcf." + permission);
    }

    public static boolean has(Player p, String permission) {
        return p.hasPermission("*") || p.hasPermission("hcf.*") || p.hasPermission("hcf." + permission);
    }

    public static boolean has(CommandSender sender, String permission) {
        return sender.hasPermission("*") || sender.hasPermission("hcf.*") || sender.hasPermission("hcf." + permission);
    }

    public static boolean has(CommandSender p, String permission, String type) {
        return p.hasPermission("*") || p.hasPermission("hcf.*") || p.hasPermission("hcf." + type) || p.hasPermission("hcf." + permission);
    }
}
