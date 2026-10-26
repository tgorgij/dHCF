package me.Thomas.HCF.IO.Claims;

import java.util.*;

/** Small claims use chunk buckets; large system claims never expand into millions of entries. */
public final class ClaimIndex {
    private static final long MAX_INDEXED_CHUNKS = 4096;
    private final Map<ChunkKey, List<Claim>> chunks = new HashMap<>();
    private final Map<UUID, List<Claim>> worlds = new HashMap<>();
    private final Map<UUID, List<Claim>> largeClaims = new HashMap<>();
    private final Map<UUID, List<Claim>> factions = new HashMap<>();

    private static long chunkCount(Claim claim) {
        return ((long) (claim.maxX() >> 4) - (claim.minX() >> 4) + 1)
                * ((long) (claim.maxZ() >> 4) - (claim.minZ() >> 4) + 1);
    }

    public void add(Claim claim) {
        worlds.computeIfAbsent(claim.world(), ignored -> new ArrayList<>()).add(claim);
        factions.computeIfAbsent(claim.faction(), ignored -> new ArrayList<>()).add(claim);
        if (chunkCount(claim) > MAX_INDEXED_CHUNKS) {
            largeClaims.computeIfAbsent(claim.world(), ignored -> new ArrayList<>()).add(claim);
            return;
        }
        for (int x = claim.minX() >> 4; x <= claim.maxX() >> 4; x++) {
            for (int z = claim.minZ() >> 4; z <= claim.maxZ() >> 4; z++) {
                chunks.computeIfAbsent(new ChunkKey(claim.world(), x, z), ignored -> new ArrayList<>()).add(claim);
            }
        }
    }

    public List<Claim> atChunk(ChunkKey key) {
        List<Claim> small = chunks.getOrDefault(key, List.of());
        List<Claim> large = largeClaims.getOrDefault(key.worldId(), List.of());
        if (large.isEmpty()) return Collections.unmodifiableList(small);
        List<Claim> result = new ArrayList<>(small);
        for (Claim claim : large) {
            if (key.chunkX() >= claim.minX() >> 4 && key.chunkX() <= claim.maxX() >> 4
                    && key.chunkZ() >= claim.minZ() >> 4 && key.chunkZ() <= claim.maxZ() >> 4) result.add(claim);
        }
        return result;
    }

    public boolean intersects(Claim candidate) {
        if (chunkCount(candidate) > MAX_INDEXED_CHUNKS) {
            return worlds.getOrDefault(candidate.world(), List.of()).stream().anyMatch(candidate::intersects);
        }
        for (Claim large : largeClaims.getOrDefault(candidate.world(), List.of())) {
            if (large.intersects(candidate)) return true;
        }
        for (int x = candidate.minX() >> 4; x <= candidate.maxX() >> 4; x++) {
            for (int z = candidate.minZ() >> 4; z <= candidate.maxZ() >> 4; z++) {
                for (Claim existing : chunks.getOrDefault(new ChunkKey(candidate.world(), x, z), List.of())) {
                    if (existing.intersects(candidate)) return true;
                }
            }
        }
        return false;
    }

    public boolean hasFaction(UUID faction) { return factions.containsKey(faction); }

    public void remove(Claim claim) {
        Set<Claim> removed = Set.of(claim);
        removeFrom(chunks, removed);
        removeFrom(worlds, removed);
        removeFrom(largeClaims, removed);
        removeFrom(factions, removed);
    }

    public void removeFaction(UUID faction) {
        List<Claim> removed = factions.remove(faction);
        if (removed == null) return;
        Set<Claim> removing = new HashSet<>(removed);
        removeFrom(chunks, removing);
        removeFrom(worlds, removing);
        removeFrom(largeClaims, removing);
    }

    private static <K> void removeFrom(Map<K, List<Claim>> index, Set<Claim> removed) {
        index.values().removeIf(claims -> {
            claims.removeIf(removed::contains);
            return claims.isEmpty();
        });
    }

    public void clear() {
        chunks.clear(); worlds.clear(); largeClaims.clear(); factions.clear();
    }
}
