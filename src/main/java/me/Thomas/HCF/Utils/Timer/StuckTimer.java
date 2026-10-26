package me.Thomas.HCF.Utils.Timer;

import me.Thomas.HCF.IO.Claims.Claim;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

/** Stuck warmup, candidate cache and incremental destination search. World access stays on the main thread. */
public final class StuckTimer extends Timer {
    private static final int CACHE_SIZE = 3;
    private static final int HEIGHT_CHECKS_PER_SLICE = 128;
    private static final Set<Material> HAZARDS = Set.of(Material.LAVA, Material.WATER,
            Material.FIRE, Material.SOUL_FIRE, Material.CACTUS, Material.MAGMA_BLOCK,
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW,
            Material.SWEET_BERRY_BUSH, Material.WITHER_ROSE, Material.END_PORTAL,
            Material.NETHER_PORTAL, Material.BUBBLE_COLUMN, Material.POINTED_DRIPSTONE);

    private record Column(int x, int z) {}

    private final Location origin;
    private final World world;
    private final Claim claim;
    private Player player;
    private boolean queued;
    private final int maxColumns;
    private final int maxBorderDistance;
    private final PriorityQueue<Column> pending;
    private final Set<Column> visited = new HashSet<>();
    private int examined;
    private boolean loading;
    private boolean warmingUp = true;
    private boolean cacheChecked;
    private boolean finished;
    private boolean loadFailed;
    private Column scanning;
    private int heightCursor;
    private final List<Location> cached = new ArrayList<>(CACHE_SIZE);

    public StuckTimer(Location origin, Claim claim) {
        super(Type.STUCK);
        this.origin = origin.clone();
        world = origin.getWorld();
        this.claim = claim;
        maxColumns = Math.max(1, Main.getInstance().getConfig().getInt("factions.stuck.max-search-columns", 8192));
        maxBorderDistance = Math.clamp(Main.getInstance().getConfig().getInt("factions.stuck.max-border-distance", 32), 1, 1024);
        pending = new PriorityQueue<>(Comparator.comparingLong(this::borderDistanceSquared)
                .thenComparingDouble(this::originDistanceSquared)
                .thenComparingInt(Column::x).thenComparingInt(Column::z));
        resetSearch();
    }

    public boolean isOutsideRadius(Location location) {
        return !world.equals(location.getWorld()) || origin.distanceSquared(location) > 25.0;
    }

    public void start(Player player) {
        this.player = player;
        enqueue(this);
        Main.getInstance().getServer().getScheduler().runTaskLater(Main.getInstance(), () -> {
            if (!isActive()) return;
            warmingUp = false;
            enqueue(this);
        }, Type.STUCK.getLength() * 20L);
    }

    private boolean isActive() {
        return player.isOnline() && User.get(player).getTimer(Type.STUCK) == this;
    }

    /** Returns true when this job can leave the scheduler until explicitly submitted again. */
    private boolean advance(long deadline) {
        if (finished) return true;
        if (!isActive()) {
            cached.clear();
            finished = true;
            return true;
        }
        if (!warmingUp && !cacheChecked) {
            while (!cached.isEmpty() && System.nanoTime() < deadline) {
                Location candidate = cached.removeFirst();
                if (isSafe(candidate)) return finish(candidate);
            }
            if (!cached.isEmpty()) return false;
            cacheChecked = true;
            // Earlier rejected columns may have changed during the countdown as well.
            resetSearch();
        }
        if (claim == null) return warmingUp || finish(isSafe(origin) ? origin : null);
        if (warmingUp && cached.size() >= CACHE_SIZE) return true;
        if (loadFailed) return finish(null);
        if (loading) return false;

        int checks = 0;
        while (checks++ < HEIGHT_CHECKS_PER_SLICE && System.nanoTime() < deadline) {
            if (scanning == null) {
                if (pending.isEmpty() || examined >= maxColumns) return warmingUp || finish(null);
                Column column = pending.peek();
                Location probe = at(column, origin.getBlockY());
                // Claims cover the whole column: check once, rather than once per height.
                boolean available = Main.getInstance().getClaimAt(probe) == null
                        && world.getWorldBorder().isInside(probe);
                if (available && !world.isChunkLoaded(column.x() >> 4, column.z() >> 4)) {
                    // Speculative work never loads or generates chunks.
                    if (warmingUp) return true;
                    loading = true;
                    world.getChunkAtAsync(column.x() >> 4, column.z() >> 4).whenComplete((chunk, error) -> {
                        loading = false;
                        loadFailed = error != null;
                    });
                    return false;
                }
                pending.remove();
                examined++;
                offer(column.x() - 1, column.z());
                offer(column.x() + 1, column.z());
                offer(column.x(), column.z() - 1);
                offer(column.x(), column.z() + 1);
                if (!available) continue;
                scanning = column;
                heightCursor = 0;
            }
            // A chunk can unload between slices. Restart this column after loading in fallback.
            if (!world.isChunkLoaded(scanning.x() >> 4, scanning.z() >> 4)) {
                pending.add(scanning);
                examined--;
                scanning = null;
                continue;
            }
            int range = world.getMaxHeight() - world.getMinHeight();
            if (heightCursor >= range * 2) {
                scanning = null;
                continue;
            }
            int first = Math.clamp(origin.getBlockY(), world.getMinHeight() + 1, world.getMaxHeight() - 2);
            int offset = (heightCursor + 1) / 2;
            int y = first + (heightCursor++ % 2 == 1 ? offset : -offset);
            if (y < world.getMinHeight() + 1 || y + 1.8 >= world.getMaxHeight()) continue;
            Block floor = world.getBlockAt(scanning.x(), y - 1, scanning.z());
            // Most heights are air: avoid allocating locations or doing full checks for them.
            if (floor.isPassable() || floor.isLiquid() || HAZARDS.contains(floor.getType())) continue;
            Location destination = at(scanning, y);
            if (isSafeBody(destination)) {
                scanning = null;
                if (!warmingUp) {
                    // Recheck territory in case the claim changed between height slices.
                    if (isSafe(destination)) return finish(destination);
                } else {
                    cached.add(destination);
                    if (cached.size() >= CACHE_SIZE) return true;
                }
            }
        }
        return false;
    }

    private void resetSearch() {
        pending.clear();
        visited.clear();
        scanning = null;
        examined = 0;
        loadFailed = false;
        if (claim == null) return;
        int x = Math.clamp(origin.getBlockX(), claim.minX(), claim.maxX());
        int z = Math.clamp(origin.getBlockZ(), claim.minZ(), claim.maxZ());
        offer(claim.minX() - 1, z);
        offer(claim.maxX() + 1, z);
        offer(x, claim.minZ() - 1);
        offer(x, claim.maxZ() + 1);
    }

    private Location at(Column column, int y) {
        return new Location(world, column.x() + 0.5, y, column.z() + 0.5,
                origin.getYaw(), origin.getPitch());
    }

    public static boolean isSafe(Location location) {
        World world = location.getWorld();
        if (world == null || !world.getWorldBorder().isInside(location)) return false;
        // Check the entire footprint before accessing blocks, without synchronous chunk loads.
        for (int x = (int) Math.floor(location.getX() - 0.3); x <= (int) Math.floor(location.getX() + 0.3); x++) {
            for (int z = (int) Math.floor(location.getZ() - 0.3); z <= (int) Math.floor(location.getZ() + 0.3); z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)
                        || Main.getInstance().getClaimAt(new Location(world, x, location.getY(), z)) != null) return false;
            }
        }
        return isSafeBody(location);
    }

    private static boolean isSafeBody(Location location) {
        World world = location.getWorld();
        if (location.getY() < world.getMinHeight() + 1 || location.getY() + 1.8 >= world.getMaxHeight()) return false;
        // Require a supporting surface wide enough for the player's standing body.
        Block floor = world.getBlockAt(location.clone().subtract(0, 0.01, 0));
        if (floor.isPassable() || floor.isLiquid() || HAZARDS.contains(floor.getType())) return false;
        BoundingBox floorBox = floor.getBoundingBox();
        if (Math.abs(floorBox.getMaxY() - location.getY()) > 0.001
                || floorBox.getMinX() > location.getX() - 0.3
                || floorBox.getMaxX() < location.getX() + 0.3
                || floorBox.getMinZ() > location.getZ() - 0.3
                || floorBox.getMaxZ() < location.getZ() + 0.3) return false;
        BoundingBox body = new BoundingBox(location.getX() - 0.3, location.getY(), location.getZ() - 0.3,
                location.getX() + 0.3, location.getY() + 1.8, location.getZ() + 0.3);
        // Check every block touched by a standing player's body, including fractional origins.
        for (int x = (int) Math.floor(location.getX() - 0.3); x <= (int) Math.floor(location.getX() + 0.3); x++) {
            for (int z = (int) Math.floor(location.getZ() - 0.3); z <= (int) Math.floor(location.getZ() + 0.3); z++) {
                for (int y = location.getBlockY(); y <= (int) Math.floor(location.getY() + 1.8); y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if ((!block.isPassable() && block.getBoundingBox().overlaps(body))
                            || block.isLiquid() || HAZARDS.contains(block.getType())) return false;
                }
            }
        }
        return true;
    }

    private void offer(int x, int z) {
        if (claim.contains(x, z)) return;
        Column column = new Column(x, z);
        if (borderDistanceSquared(column) <= (long) maxBorderDistance * maxBorderDistance && visited.add(column)) {
            pending.add(column);
        }
    }

    private long borderDistanceSquared(Column column) {
        long dx = Math.max(Math.max((long) claim.minX() - column.x(), (long) column.x() - claim.maxX()), 0);
        long dz = Math.max(Math.max((long) claim.minZ() - column.z(), (long) column.z() - claim.maxZ()), 0);
        return dx * dx + dz * dz;
    }

    private double originDistanceSquared(Column column) {
        double dx = column.x() + 0.5 - origin.getX();
        double dz = column.z() + 0.5 - origin.getZ();
        return dx * dx + dz * dz;
    }

    private boolean finish(Location destination) {
        finished = true;
        cached.clear();
        pending.clear();
        visited.clear();
        if (isActive()) {
            User.get(player).removeTimer(Type.STUCK);
            boolean teleported = destination != null && player.teleport(destination);
            player.sendMessage(C.chat(Objects.requireNonNull(Main.getInstance().getLocaleConfig().getString(
                    "commands.faction.stuck." + (teleported ? "completed" : "no-safe-location")))));
        }
        return true;
    }

    // Shared soft budgets: precomputation cannot multiply its cost with the player count.
    private static final long TICK_BUDGET_NANOS = 500_000L;
    private static final long WARMUP_BUDGET_NANOS = 100_000L;
    private static final ArrayDeque<StuckTimer> warmups = new ArrayDeque<>();
    private static final ArrayDeque<StuckTimer> fallbacks = new ArrayDeque<>();
    private static BukkitTask worker;

    private static void enqueue(StuckTimer timer) {
        if (!timer.queued) {
            (timer.warmingUp ? warmups : fallbacks).addLast(timer);
            timer.queued = true;
        } else if (!timer.warmingUp && warmups.remove(timer)) {
            fallbacks.addLast(timer);
        }
        if (worker == null) worker = Main.getInstance().getServer().getScheduler()
                .runTaskTimer(Main.getInstance(), StuckTimer::tickSearches, 0L, 1L);
    }

    private static void tickSearches() {
        long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
        advance(fallbacks, deadline - WARMUP_BUDGET_NANOS);
        advance(warmups, Math.min(deadline, System.nanoTime() + WARMUP_BUDGET_NANOS));
        if (warmups.isEmpty() && fallbacks.isEmpty()) shutdownSearches();
    }

    private static void advance(ArrayDeque<StuckTimer> queue, long deadline) {
        int turns = queue.size();
        while (turns-- > 0 && System.nanoTime() < deadline) {
            StuckTimer timer = queue.removeFirst();
            if (timer.advance(Math.min(deadline, System.nanoTime() + 250_000L))) timer.queued = false;
            else queue.addLast(timer);
        }
    }

    public static void shutdownSearches() {
        if (worker != null) worker.cancel();
        worker = null;
        warmups.clear();
        fallbacks.clear();
    }
}
