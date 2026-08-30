package me.Thomas.HCF.IO.Claims;

import me.Thomas.HCF.Main.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.UUID;

public record Claim(
        UUID faction,
        Location one,
        Location two
) {
    public Claim(
            UUID faction,
            UUID worldId,
            int minX,
            int maxX,
            int minZ,
            int maxZ
    ) {
        this(
                faction,
                createLocation(worldId, minX, minZ),
                createLocation(worldId, maxX, maxZ)
        );
    }

    private static Location createLocation(
            UUID worldId,
            int x,
            int z
    ) {
        World world = Bukkit.getWorld(worldId);

        if (world == null) {
            throw new IllegalArgumentException(
                    "World is not loaded: " + worldId
            );
        }

        return new Location(world, x, 0, z);
    }

    public UUID world() {
        return one.getWorld().getUID();
    }

    public int minX() {
        return Math.min(
                one.getBlockX(),
                two.getBlockX()
        );
    }

    public int maxX() {
        return Math.max(
                one.getBlockX(),
                two.getBlockX()
        );
    }

    public int minZ() {
        return Math.min(
                one.getBlockZ(),
                two.getBlockZ()
        );
    }

    public int maxZ() {
        return Math.max(
                one.getBlockZ(),
                two.getBlockZ()
        );
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
        int minChunkX = Math.floorDiv(minX(), 16);
        int maxChunkX = Math.floorDiv(maxX(), 16);
        int minChunkZ = Math.floorDiv(minZ(), 16);
        int maxChunkZ = Math.floorDiv(maxZ(), 16);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkKey key = new ChunkKey(
                        world(),
                        chunkX,
                        chunkZ
                );

                List<Claim> claimsInChunk =
                        Main.getInstance().getClaimsByChunk(key);

                if (claimsInChunk == null) {
                    continue;
                }

                for (Claim existing : claimsInChunk) {
                    if (existing.intersects(this)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}