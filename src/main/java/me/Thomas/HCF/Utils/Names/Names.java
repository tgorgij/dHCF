package me.Thomas.HCF.Utils.Names;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public class Names {

    public static boolean isInvalid(String name, CommandSender sender) {
        FileConfiguration config = Main.getInstance().getConfig();
        String path = config.isConfigurationSection("settings.names") ? "settings.names" : "factions.name";
        if (config.getStringList(path + ".blocked").stream().anyMatch(name::equalsIgnoreCase)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.name.blocked")));
            return true;
        }
        int minimum = config.getInt(path + ".min", 3);
        int maximum = config.getInt(path + ".max", 16);
        if (name.length() < minimum || name.length() > maximum) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.name.invalid-length")
                    .replace("%min%", Integer.toString(minimum)).replace("%max%", Integer.toString(maximum))));
            return true;
        }
        return false;
    }
}
