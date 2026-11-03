package me.Thomas.HCF.Utils.Deathban;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.util.Map;
import java.util.function.Predicate;

public final class Deathban {

    private Deathban() {
    }

    public static long getDuration(Player player) {
        return getDuration(Main.getInstance().getConfig(), player::hasPermission);
    }

    public static long getDuration(ConfigurationSection config, Predicate<String> hasPermission) {
        if (!config.getBoolean("settings.features.deathbans", true)) return 0L;
        long duration = Long.MAX_VALUE;
        boolean matched = false;
        for (Map<?, ?> entry : config.getMapList("deathbans.permissions")) {
            if (entry.get("permission") instanceof String permission
                    && entry.get("duration") instanceof Number seconds && hasPermission.test(permission)) {
                duration = Math.clamp(seconds.longValue(), 0L, duration);
                matched = true;
            }
        }
        return matched ? duration : Math.max(0L, config.getLong("deathbans.default-duration", 3600L));
    }

    public static void apply(User user, long seconds) {
        if (!Main.getInstance().isFeatureEnabled("deathbans")) return;
        user.setDeathbanExpiry(getExpiry(System.currentTimeMillis(), seconds));
        user.saveAsync();
    }

    public static void apply(User user, long seconds, Location deathLocation) {
        if (!Main.getInstance().isFeatureEnabled("deathbans")) return;
        Faction territory = Faction.getAtLocation(deathLocation);
        if (territory == null || territory.isDeathban()) apply(user, seconds);
    }

    public static long getExpiry(long now, long seconds) {
        if (seconds <= 0L) return 0L;
        long safeSeconds = Math.min(seconds, (Long.MAX_VALUE - now) / 1000L);
        return now + safeSeconds * 1000L;
    }

    public static long getRemainingSeconds(long expiry, long now) {
        if (expiry <= now) return 0L;
        long remaining = expiry - now;
        return remaining / 1000L + (remaining % 1000L == 0L ? 0L : 1L);
    }
}
