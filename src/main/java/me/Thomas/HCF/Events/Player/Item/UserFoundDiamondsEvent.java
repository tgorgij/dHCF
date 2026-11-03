package me.Thomas.HCF.Events.Player.Item;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class UserFoundDiamondsEvent implements Listener {

    private record Position(int x, int y, int z) {
        private static Position of(Block block) { return new Position(block.getX(), block.getY(), block.getZ()); }
        private long packed() { return (long) y << 8 | (x & 15) << 4 | z & 15; }
    }

    private final NamespacedKey ignoredKey = new NamespacedKey(Main.getInstance(), "found-ores-ignored");
    private final Set<Material> ores = new HashSet<>();

    public UserFoundDiamondsEvent() {
        for (String configured : Main.getInstance().getConfig().getStringList("found-diamonds.ores")) {
            Material material = Material.matchMaterial(configured);
            if (material != null && material.isBlock() && !material.isAir()) ores.add(material);
        }
    }

    private Set<Long> ignored(Chunk chunk) {
        Set<Long> positions = new HashSet<>();
        long[] stored = chunk.getPersistentDataContainer().get(ignoredKey, PersistentDataType.LONG_ARRAY);
        if (stored != null) for (long position : stored) positions.add(position);
        return positions;
    }

    private void store(Chunk chunk, Set<Long> positions) {
        if (positions.isEmpty()) chunk.getPersistentDataContainer().remove(ignoredKey);
        else chunk.getPersistentDataContainer().set(ignoredKey, PersistentDataType.LONG_ARRAY,
                positions.stream().mapToLong(Long::longValue).sorted().toArray());
    }

    private String group(Material material) { return material.name().replaceFirst("^DEEPSLATE_", ""); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block block = e.getBlockPlaced();
        if (!ores.contains(block.getType())) return;
        Set<Long> positions = ignored(block.getChunk());
        positions.add(Position.of(block).packed());
        store(block.getChunk(), positions);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block origin = e.getBlock();
        if (!ores.contains(origin.getType())) return;
        Set<Long> positions = ignored(origin.getChunk());
        if (positions.remove(Position.of(origin).packed())) {
            store(origin.getChunk(), positions);
            return;
        }
        if (e.getPlayer().getGameMode() == GameMode.CREATIVE || User.get(e.getPlayer()).isInStaffMode()) return;
        World world = origin.getWorld();
        String group = group(origin.getType());
        int limit = Math.clamp(Main.getInstance().getConfig().getInt("found-diamonds.max-vein-size", 64), 1, 256);
        ArrayDeque<Position> pending = new ArrayDeque<>();
        Set<Position> visited = new HashSet<>();
        List<Block> vein = new ArrayList<>();
        Map<Chunk, Set<Long>> chunks = new HashMap<>();
        chunks.put(origin.getChunk(), positions);
        pending.add(Position.of(origin));
        while (!pending.isEmpty() && vein.size() < limit) {
            Position position = pending.removeFirst();
            if (!visited.add(position) || position.y() < world.getMinHeight() || position.y() >= world.getMaxHeight()
                    || !world.isChunkLoaded(position.x() >> 4, position.z() >> 4)) continue;
            Block block = world.getBlockAt(position.x(), position.y(), position.z());
            if (!ores.contains(block.getType()) || !group.equals(group(block.getType()))
                    || chunks.computeIfAbsent(block.getChunk(), this::ignored).contains(position.packed())) continue;
            vein.add(block);
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                if (x != 0 || y != 0 || z != 0) pending.add(new Position(position.x() + x, position.y() + y, position.z() + z));
            }
        }
        if (vein.isEmpty()) return;
        Set<Chunk> changed = new HashSet<>();
        for (Block block : vein) {
            if (Position.of(block).equals(Position.of(origin))) continue;
            chunks.get(block.getChunk()).add(Position.of(block).packed());
            changed.add(block.getChunk());
        }
        for (Chunk chunk : changed) store(chunk, chunks.get(chunk));
        Bukkit.broadcast(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.found-diamonds")
                .replace("%player%", e.getPlayer().getName()).replace("%amount%", Integer.toString(vein.size()))
                .replace("%ore%", group.toLowerCase(Locale.ROOT).replace('_', ' '))));
    }

    private void move(List<Block> blocks, BlockFace direction) {
        Map<Chunk, Set<Long>> chunks = new HashMap<>();
        List<Block> moved = blocks.stream().filter(block -> ores.contains(block.getType())).toList();
        for (Block block : moved) chunks.computeIfAbsent(block.getChunk(), this::ignored).remove(Position.of(block).packed());
        for (Block block : moved) {
            Block destination = block.getRelative(direction);
            chunks.computeIfAbsent(destination.getChunk(), this::ignored).add(Position.of(destination).packed());
        }
        chunks.forEach(this::store);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent e) {
        BlockFace direction = e.getBlock().getBlockData() instanceof Directional data ? data.getFacing() : e.getDirection();
        move(e.getBlocks(), direction);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent e) {
        BlockFace direction = e.getBlock().getBlockData() instanceof Directional data ? data.getFacing() : e.getDirection();
        move(e.getBlocks(), direction.getOppositeFace());
    }

    private void destroyed(List<Block> blocks) {
        Map<Chunk, Set<Long>> chunks = new HashMap<>();
        for (Block block : blocks) {
            if (ores.contains(block.getType())) chunks.computeIfAbsent(block.getChunk(), this::ignored).remove(Position.of(block).packed());
        }
        chunks.forEach(this::store);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent e) { destroyed(e.blockList()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent e) { destroyed(e.blockList()); }
}
