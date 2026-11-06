package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class HomeCommand implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();
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
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.home.usage").replace("%alias%", alias)));
            return true;
        }
        User user = User.get(p);
        if (!user.hasFaction()) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.not-in")));
            return true;
        }
        Faction faction = Faction.get(user.getFaction());
        if (faction == null || faction.getHome() == null) {
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
            start(p, 0);
            return true;
        }

        start(p, Math.max(0, Timer.Type.HOME.getLength()));
        return true;
    }

    private static void cancelHome(Player p, User user, Timer timer, String message) {
        if (user.getTimer(Timer.Type.HOME) != timer) return;
        user.removeTimer(Timer.Type.HOME);
        if (p.isOnline() && User.get(p) == user) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString(message)));
        }
    }

    public static void start(Player p, long seconds) {
        User user = User.get(p);
        UUID factionId = user.getFaction();
        Location origin = p.getLocation().clone();
        LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();
        Timer timer = new Timer(Timer.Type.HOME, Math.addExact(System.currentTimeMillis(),
                Math.multiplyExact(seconds, 1000L)), 0L, false);
        user.addTimer(Timer.Type.HOME, timer);
        if (seconds > 0) p.sendMessage(C.chat(localeConfig.getString("commands.faction.home.success")
                .replace("%time%", C.formatChatTime(seconds))));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (user.getTimer(Timer.Type.HOME) != timer) return;
                Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
                if (!p.isOnline() || p.isDead() || User.get(p) != user || faction == null
                        || !Objects.equals(factionId, user.getFaction()) || faction.getHome() == null) {
                    cancelHome(p, user, timer, "primary.teleport.cancel");
                    return;
                }
                Location destination = faction.getHome();
                if (destination.getWorld() == null) {
                    cancelHome(p, user, timer, "commands.faction.home.failed");
                    return;
                }
                // Keep the timer cancellable while loading; commit the teleport on the server thread.
                destination.getWorld().getChunkAtAsync(destination).whenComplete((chunk, failure) -> {
                    if (!Main.getInstance().isEnabled()) return;
                    Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                        if (user.getTimer(Timer.Type.HOME) != timer) return;
                        if (!p.isOnline() || p.isDead() || User.get(p) != user) {
                            cancelHome(p, user, timer, "primary.teleport.cancel");
                            return;
                        }
                        Faction currentFaction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
                        Location current = p.getLocation();
                        if (user.hasTimer(Timer.Type.COMBAT) || !Objects.equals(factionId, user.getFaction())
                                || currentFaction == null || !currentFaction.hasMember(p.getUniqueId())
                                || !destination.equals(currentFaction.getHome())
                                || !Objects.equals(origin.getWorld(), current.getWorld())
                                || origin.getBlockX() != current.getBlockX() || origin.getBlockY() != current.getBlockY()
                                || origin.getBlockZ() != current.getBlockZ()) {
                            cancelHome(p, user, timer, "primary.teleport.cancel");
                            return;
                        }
                        if (failure != null || chunk == null || !chunk.isLoaded()) {
                            cancelHome(p, user, timer, "commands.faction.home.failed");
                            return;
                        }
                        user.removeTimer(Timer.Type.HOME);
                        boolean teleported = p.teleport(destination);
                        p.sendMessage(C.chat(localeConfig.getString(teleported ? "primary.teleport.hq" : "commands.faction.home.failed")
                                .replace("%faction%", currentFaction.getName())));
                    });
                });
            }
        }.runTaskLater(Main.getInstance(), Math.multiplyExact(seconds, 20L));
    }
}
