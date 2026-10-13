package me.Thomas.HCF.IO.Claims;

import me.Thomas.HCF.Main.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.UUID;

public record Claim(UUID faction, UUID world, int minX, int maxX, int minZ, int maxZ) {
    public static final int MIN_SIDE_LENGTH = 5;
    public static final int MAX_CHUNKS = 16;
    public static final int BLOCKS_PER_CHUNK = 16 * 16;
    public static final long MAX_AREA = (long) MAX_CHUNKS * BLOCKS_PER_CHUNK;

    public Claim {
        java.util.Objects.requireNonNull(faction);
        java.util.Objects.requireNonNull(world);
        if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("Invalid claim bounds");
    }

    public Claim(UUID faction, Location one, Location two) {
        this(faction, commonWorld(one, two),
                Math.min(one.getBlockX(), two.getBlockX()), Math.max(one.getBlockX(), two.getBlockX()),
                Math.min(one.getBlockZ(), two.getBlockZ()), Math.max(one.getBlockZ(), two.getBlockZ()));
    }

    private static UUID commonWorld(Location one, Location two) {
        if (one.getWorld() == null || !one.getWorld().equals(two.getWorld())) {
            throw new IllegalArgumentException("Claim corners must be in the same world");
        }
        return one.getWorld().getUID();
    }

    public Location one() { return new Location(Bukkit.getWorld(world), minX, 0, minZ); }
    public Location two() { return new Location(Bukkit.getWorld(world), maxX, 0, maxZ); }
    public long width() { return (long) maxX - minX + 1; }
    public long length() { return (long) maxZ - minZ + 1; }
    public long area() {
        return width() > Long.MAX_VALUE / length() ? Long.MAX_VALUE : width() * length();
    }

    public boolean isTooSmall() {
        return width() < MIN_SIDE_LENGTH || length() < MIN_SIDE_LENGTH;
    }

    public boolean isTooLarge() {
        return area() > MAX_AREA;
    }

    public double price() {
        double basePrice = Main.getInstance().getConfig().getDouble("factions.claim.price.base");
        double pricePerBlock = Main.getInstance().getConfig().getDouble("factions.claim.price.per-block");
        long includedArea = (long) MIN_SIDE_LENGTH * MIN_SIDE_LENGTH;
        return basePrice + Math.max(0L, area() - includedArea) * pricePerBlock;
    }

    public boolean contains(int x, int z) {
        return x >= minX()
                && x <= maxX()
                && z >= minZ()
                && z <= maxZ();
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        if (!world().equals(location.getWorld().getUID())) {
            return false;
        }

        return contains(
                location.getBlockX(),
                location.getBlockZ()
        );
    }

    public boolean intersects(Claim other) {
        if (other == null) {
            return false;
        }

        if (!world().equals(other.world())) {
            return false;
        }

        return minX() <= other.maxX()
                && maxX() >= other.minX()
                && minZ() <= other.maxZ()
                && maxZ() >= other.minZ();
    }

    public boolean intersects() {
        return Main.getInstance().intersectsClaim(this);
    }
}
