package me.Thomas.HCF.Commands.Admin.SetEnd;

import me.Thomas.HCF.Commands.Admin.SetEnd.Subcommands.EntranceCommand;
import me.Thomas.HCF.Commands.Admin.SetEnd.Subcommands.ExitCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public class SetEndCommand implements TabExecutor {
    private final Map<String, Subcommand> subcommands = new HashMap<>();

    public SetEndCommand() {
        register(new EntranceCommand());
        register(new ExitCommand());
    }

    private void register(Subcommand subcommand) {
        subcommands.put(subcommand.getName(), subcommand);
        for (String alias : subcommand.getAliases()) subcommands.put(alias, subcommand);
    }

    public static void send(CommandSender sender, String key, String... replacements) {
        String message = Main.getInstance().getLocaleConfig().getString("commands.setend." + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) message = message.replace(replacements[i], replacements[i + 1]);
        sender.sendMessage(C.chat(message));
    }

    public static void setLocation(Player p, String destination) {
        Location location = p.getLocation();
        boolean entrance = destination.equals("entrance");
        if ((location.getWorld().getEnvironment() == World.Environment.THE_END) != entrance) {
            send(p, entrance ? "entrance-world" : "exit-world");
            return;
        }
        if (location.getY() < location.getWorld().getMinHeight() || location.getY() >= location.getWorld().getMaxHeight()) {
            send(p, "invalid-height");
            return;
        }
        String path = "end." + destination;
        Object previous = Main.getInstance().getConfig().get(path);
        Main.getInstance().getConfig().createSection(path, Map.of("world", location.getWorld().getUID().toString(),
                "x", location.getX(), "y", location.getY(), "z", location.getZ(), "yaw", location.getYaw(), "pitch", location.getPitch()));
        try { Main.getInstance().getConfig().save(new File(Main.getInstance().getDataFolder(), "config.yml")); }
        catch (IOException exception) {
            Main.getInstance().getConfig().set(path, previous);
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not save End " + destination, exception);
            send(p, "failed");
            return;
        }
        send(p, destination + "-set");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.setend", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        Subcommand subcommand = args.length == 0 ? null : subcommands.get(args[0].toLowerCase(Locale.ROOT));
        if (subcommand == null) { send(sender, "usage", "%alias%", alias); return true; }
        return subcommand.execute(sender, Arrays.copyOfRange(args, 1, args.length), alias);
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.setend", "admin") || args.length != 1) return List.of();
        return subcommands.keySet().stream().filter(name -> name.startsWith(args[0].toLowerCase(Locale.ROOT))).sorted().toList();
    }
}
