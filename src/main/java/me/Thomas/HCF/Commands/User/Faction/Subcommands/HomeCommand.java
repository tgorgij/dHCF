package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

public class HomeCommand implements Subcommand {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();
    private final boolean disabledInEnd = Main.getInstance().getConfig().getBoolean("factions.home.disable-in-end");

    @Override
    public String getName() {
        return "home";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
            return true;
        }
        if (args.length != 0) {
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.home.usage")).replace("%alias%", alias)));
            return true;
        }
        User user = User.get(p);
        if (user.hasTimer(Timer.Type.COMBAT)) {
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("primary.combat-tagged"))));
            return true;
        }
        if (!user.hasFaction()) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.not-in")));
            return true;
        }
        Faction faction = Faction.get(user.getFaction());
        if (faction.getHome() == null) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.home.none")));
            return true;
        }
        if (disabledInEnd && p.getLocation().getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.home.end")));
            return true;
        }

        Faction factionAtLocation = Faction.getAtLocation(p.getLocation());
        if (user.getTimer(Timer.Type.HOME) != null || user.getTimer(Timer.Type.STUCK) != null) {
            p.sendMessage(C.chat(localeConfig.getString("primary.teleport.already")));
            return true;
        }
        if (factionAtLocation != null && factionAtLocation.getType() == Faction.Type.SAFEZONE) {
            p.teleportAsync(faction.getHome());
            return true;
        }

        Timer timer = new Timer(Timer.Type.HOME);
        user.addTimer(Timer.Type.HOME, timer);
        p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.home.success"))));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (user.getTimer(Timer.Type.HOME) != timer) {
                    return;
                }

                user.removeTimer(Timer.Type.HOME);

                if (!p.isOnline() || !user.hasFaction()) {
                    return;
                }

                Faction currentFaction = Main.getInstance().getAllFactions().get(user.getFaction());
                if (currentFaction == null || currentFaction.getHome() == null) {
                    return;
                }

                p.teleportAsync(currentFaction.getHome());
                p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("primary.teleport.hq")).replace("%faction%", currentFaction.getName())));
            }
        }.runTaskLater(Main.getInstance(), Timer.Type.HOME.getLength() * 20L);

        return true;
    }
}
