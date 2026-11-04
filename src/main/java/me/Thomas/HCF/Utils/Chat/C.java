package me.Thomas.HCF.Utils.Chat;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;

import java.text.DecimalFormat;
import java.util.Locale;

public class C {

    public static Component chat(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(replacePlaceholders(s == null ? "" : s));
    }

    public static String getColorCode(String colorName) {
        return switch (colorName.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "")) {
            case "black" -> "&0";
            case "darkblue" -> "&1";
            case "green" -> "&2";
            case "cyan" -> "&3";
            case "darkred" -> "&4";
            case "purple" -> "&5";
            case "gold" -> "&6";
            case "gray", "grey" -> "&7";
            case "darkgray", "darkgrey" -> "&8";
            case "blue" -> "&9";
            case "lime" -> "&a";
            case "aqua" -> "&b";
            case "red" -> "&c";
            case "pink" -> "&d";
            case "yellow" -> "&e";
            case "white" -> "&f";
            default -> "";
        };
    }

    public static String getPlayerColor(User viewer, User target) {
        String relation = "enemy";
        if (viewer != null) {
            if (viewer.getUUID().equals(target.getUUID())
                    || viewer.hasFaction() && viewer.getFaction().equals(target.getFaction())) relation = "team";
            else if (viewer.hasFaction()) {
                Faction faction = Faction.get(viewer.getFaction());
                if (faction != null && faction.isAllied(target.getFaction())) relation = "ally";
                Main.Focus focus = Main.getInstance().getFocus(viewer.getFaction());
                if (focus != null && (focus.faction() ? focus.target().equals(target.getFaction())
                        : focus.target().equals(target.getUUID()))) relation = "focus";
            }
        }
        String fallback = switch (relation) {
            case "team" -> "GREEN";
            case "focus" -> "PINK";
            case "ally" -> "BLUE";
            default -> "RED";
        };
        String color = getColorCode(Main.getInstance().getConfig().getString("factions.colors." + relation, fallback));
        return color.isEmpty() ? getColorCode(fallback) : color;
    }

    public static String getFactionName(User viewer, User target) {
        Faction faction = target.hasFaction() ? Faction.get(target.getFaction()) : null;
        return faction == null ? Main.getInstance().getLocaleConfig().getString("events.player.chat.no-faction", "&6*")
                : getPlayerColor(viewer, target) + faction.getName();
    }

    public static String replacePlaceholders(String s) {
        if (s == null || s.isEmpty()) return s;
        if (s.contains("%server-name%")) s = s.replace("%server-name%", secureConfig("settings.server-name"));
        if (s.contains("%primary%")) s = s.replace("%primary%", getColorCode(secureConfig("settings.colors.primary")));
        if (s.contains("%secondary%")) s = s.replace("%secondary%", getColorCode(secureConfig("settings.colors.secondary")));
        if (s.contains("%map-number%")) s = s.replace("%map-number%", secureConfig("settings.map.number"));
        if (s.contains("%map-start-date%")) s = s.replace("%map-start-date%", secureConfig("settings.map.start-date"));
        return s;
    }

    private static String secureConfig(String path) {
        return Main.getInstance().getConfig().getString(path, "");
    }

    public static String formatTime(long remainingMillis) {
        return formatTime(remainingMillis, false);
    }

    public static String formatTime(long remainingMillis, boolean includeHours) {
        remainingMillis = Math.max(0L, remainingMillis);

        long totalSeconds = remainingMillis / 1000L;
        long tenths = (remainingMillis % 1000L) / 100L;

        if (includeHours || totalSeconds >= 3600) {
            long hours = totalSeconds / 3600L;
            long minutes = (totalSeconds % 3600L) / 60L;
            long seconds = totalSeconds % 60L;

            return (hours < 10 ? "0" : "") + hours + ":" + twoDigits(minutes) + ":" + twoDigits(seconds);
        }

        if (totalSeconds >= 60) {
            long minutes = totalSeconds / 60L;
            long seconds = totalSeconds % 60L;

            return minutes + ":" + twoDigits(seconds);
        }

        return totalSeconds + "." + tenths + "s";
    }

    private static String twoDigits(long value) {
        return (value < 10 ? "0" : "") + value;
    }

    public static String formatChatTime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;

        StringBuilder result = new StringBuilder();

        if (hours > 0) {
            result.append(hours)
                    .append(hours == 1 ? " hour" : " hours");
        }

        if (minutes > 0) {
            if (!result.isEmpty()) {
                result.append(" ");
            }

            result.append(minutes)
                    .append(minutes == 1 ? " minute" : " minutes");
        }

        if (remainingSeconds > 0 || result.isEmpty()) {
            if (!result.isEmpty()) {
                result.append(" ");
            }

            result.append(remainingSeconds)
                    .append(remainingSeconds == 1 ? " second" : " seconds");
        }

        return result.toString();
    }

    public static String getFormattedMoney(double amount) {
        DecimalFormat formatter = new DecimalFormat(Main.getInstance().getConfig().getString("economy.currency-symbol", "$") + "#,##0.00");
        return formatter.format(amount);
    }

    public static String getFormattedLocation(Location location) {
        return Main.getInstance().getConfig().getString("settings.location-format", "X: %x%, Z: %z%")
                .replace("%x%", Integer.toString(location.getBlockX()))
                .replace("%y%", Integer.toString(location.getBlockY()))
                .replace("%z%", Integer.toString(location.getBlockZ()));
    }

    public static String getFormattedDimension(World world) {
        return switch (world.getEnvironment()) {
            case NORMAL -> "Overworld";
            case NETHER -> "Nether";
            case THE_END -> "End";
            case CUSTOM -> world.getName();
        };
    }

    public static String getFormattedDTR(me.Thomas.HCF.IO.Faction.Faction faction) {
        String status = faction.getDTRFreezeExpiry() > System.currentTimeMillis() ? " ■"
                : faction.getDTR() < faction.getMaxDTR() && faction.getDTRNextRegen() != 0 ? "^" : "";
        return getFormattedDTR(faction.getDTR()) + status;
    }

    public static String getFormattedDTR(double dtr) {
        if (dtr <= 0) {
            return C.getColorCode(Main.getInstance().getConfig().getString("factions.dtr.colors.raidable", "RED")) + dtr;
        } else if (dtr < 1) {
            return C.getColorCode(Main.getInstance().getConfig().getString("factions.dtr.colors.close-to-raidable", "YELLOW")) + dtr;
        } else {
            return C.getColorCode(Main.getInstance().getConfig().getString("factions.dtr.colors.not-raidable", "GREEN")) + dtr;
        }
    }
}
