package me.Thomas.HCF.IO.Claims;

import java.util.UUID;

public record ChunkKey(
    UUID worldId,
    int chunkX,
    int chunkZ
) {
}