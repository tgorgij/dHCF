package me.Thomas.HCF.Utils.Combat;

import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import org.bukkit.configuration.ConfigurationSection;

import java.math.BigDecimal;

public final class DTR {
    private static final long MINUTE = 60_000L;

    private DTR() {
    }

    /** Starts regeneration or resets the freeze to its full configured duration. */
    public static void setRegenerating(Faction faction, boolean regenerating, ConfigurationSection config, long now) {
        long freezeMinutes = regenerating ? 0L
                : Math.clamp(config.getLong("factions.dtr.freeze-minutes", 30L), 0L, Long.MAX_VALUE / MINUTE);
        long expiry = addTime(now, freezeMinutes * MINUTE);
        faction.setDTRFreezeExpiry(freezeMinutes == 0L ? 0L : expiry);
        faction.setDTRNextRegen(faction.getDTR() >= faction.getMaxDTR() ? 0L : addTime(expiry, MINUTE));
    }

    public static void onDeath(Faction faction, long now) {
        onDeath(faction, Main.getInstance().getConfig(), now);
    }

    public static void onDeath(Faction faction, ConfigurationSection config, long now) {
        if (faction.getType() != Faction.Type.PLAYER) return;
        double minimum = config.getDouble("factions.dtr.minimum", -0.99);
        if (!Double.isFinite(minimum)) minimum = -0.99;
        double loss = config.getBoolean("settings.features.kitmap", false) ? config.getDouble("factions.dtr.kitmap-multiplier", 0.5) : 1.0;
        if (!Double.isFinite(loss)) loss = 0.5;
        loss = Math.clamp(loss, 0.0, 1.0);
        double remaining = BigDecimal.valueOf(faction.getDTR()).subtract(BigDecimal.valueOf(loss)).doubleValue();
        faction.setDTR(Math.max(minimum, remaining));
        long freezeMinutes = Math.clamp(config.getLong("factions.dtr.freeze-minutes", 30L), 0L, Long.MAX_VALUE / MINUTE);
        long expiry = addTime(now, freezeMinutes * MINUTE);
        faction.setDTRFreezeExpiry(expiry);
        faction.setDTRNextRegen(addTime(expiry, MINUTE));
    }

    /** Advances wall-clock state, including whole regeneration minutes elapsed while the server was offline. */
    public static void tick(Faction faction, ConfigurationSection config, long now) {
        if (faction.getType() != Faction.Type.PLAYER) return;
        double maximum = faction.getMaxDTR();
        if (faction.getDTR() > maximum) faction.setDTR(maximum);
        if (now < faction.getDTRFreezeExpiry()) return;
        faction.setDTRFreezeExpiry(0L);
        if (faction.getDTR() >= maximum) {
            faction.setDTRNextRegen(0L);
            return;
        }
        if (faction.getDTRNextRegen() == 0L) {
            faction.setDTRNextRegen(addTime(now, MINUTE));
            return;
        }
        if (now < faction.getDTRNextRegen()) return;
        double rate = config.getDouble("factions.dtr.regeneration-per-minute", 0.03);
        if (!Double.isFinite(rate) || rate <= 0) return;
        long minutes = (now - faction.getDTRNextRegen()) / MINUTE + 1L;
        double regenerated = BigDecimal.valueOf(faction.getDTR())
                .add(BigDecimal.valueOf(rate).multiply(BigDecimal.valueOf(minutes))).doubleValue();
        faction.setDTR(Math.min(maximum, regenerated));
        faction.setDTRNextRegen(faction.getDTR() >= maximum ? 0L
                : addTime(now - (now - faction.getDTRNextRegen()) % MINUTE, MINUTE));
    }

    private static long addTime(long now, long duration) {
        return now + Math.min(duration, Long.MAX_VALUE - now);
    }
}
