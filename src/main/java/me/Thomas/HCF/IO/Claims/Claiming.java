package me.Thomas.HCF.IO.Claims;

import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Claiming {

    private static final int MAP_SEARCH_RADIUS = 2;

    private final UUID faction;
    private Location one;
    private Location two;
    private final boolean claimingFor;
    private final Material pillar;

    public Claiming(UUID faction, boolean claimingFor) {
        this.faction = faction;
        this.one = null;
        this.two = null;
        this.claimingFor = claimingFor;
        pillar = getRandomPillar();
    }

    public UUID getFaction() {
        return faction;
    }

    public Location getTwo() {
        return two;
    }

    public void setTwo(Location two) {
        this.two = two;
    }

    public Location getOne() {
        return one;
    }

    public void setOne(Location one) {
        this.one = one;
    }

    public boolean isClaimingFor() {
        return claimingFor;
    }

    public Material getPillar() {
        return pillar;
    }

    public static Material getRandomPillar() {
        List<Material> blocks = Arrays.stream(Material.values())
                .filter(Material::isBlock)
                .filter(Material::isSolid)
                .filter(material -> !Tag.DOORS.isTagged(material))
                .filter(material -> !Tag.TRAPDOORS.isTagged(material))
                .filter(material -> !Tag.STAIRS.isTagged(material))
                .filter(material -> !Tag.SLABS.isTagged(material))
                .filter(material -> !Tag.FENCES.isTagged(material))
                .filter(material -> !Tag.WALLS.isTagged(material))
                .filter(Material::isOccluding)
                .filter(material -> !Tag.PRESSURE_PLATES.isTagged(material))
                .filter(material -> !Tag.ITEMS_CANDLES.isTagged(material))
                .toList();

        return blocks.get(ThreadLocalRandom.current().nextInt(blocks.size()));
    }

    public static boolean hasVisiblePillars(Player player) {
        User user = User.get(player);
        Claiming claiming = user.getClaiming();
        return !user.getMap().isEmpty()
                || claiming != null
                && (claiming.getOne() != null || claiming.getTwo() != null);
    }

    public static boolean showNearbyClaims(Player player) {
        User user = User.get(player);
        if (!user.getMap().isEmpty()) {
            return true;
        }

        Location playerLocation = player.getLocation();
        ChunkKey chunkKey = new ChunkKey(
                player.getWorld().getUID(),
                Math.floorDiv(playerLocation.getBlockX(), 16),
                Math.floorDiv(playerLocation.getBlockZ(), 16)
        );
        List<Claim> nearbyClaims = Faction.getClaimsNearChunk(chunkKey, MAP_SEARCH_RADIUS);
        Map<UUID, Material> factionMaterials = new HashMap<>();

        for (Claim claim : nearbyClaims) {
            Faction faction = Faction.get(claim.faction());
            if (faction == null) {
                continue;
            }

            Material material = factionMaterials.computeIfAbsent(
                    faction.getUUID(),
                    ignored -> getRandomPillar()
            );
            generateClaimPillars(claim, material, player);
            user.getMap().add(claim);
        }

        for (Map.Entry<UUID, Material> entry : factionMaterials.entrySet()) {
            Faction faction = Faction.get(entry.getKey());
            if (faction == null) {
                continue;
            }

            player.sendMessage(C.chat(Objects.requireNonNull(
                    Main.getInstance().getLocaleConfig().getString("commands.faction.map.format")
            ).replace("%faction%", faction.getFormattedName(user))
                    .replace("%material%", entry.getValue().name())));
        }

        return !user.getMap().isEmpty();
    }

    public static void hidePillars(Player player) {
        User user = User.get(player);

        for (Claim claim : List.copyOf(user.getMap())) {
            resetClaimPillars(claim, player);
        }
        user.getMap().clear();

        Claiming claiming = user.getClaiming();
        if (claiming == null) {
            return;
        }
        if (claiming.getOne() != null) {
            resetPillar(claiming.getOne(), player);
        }
        if (claiming.getTwo() != null) {
            resetPillar(claiming.getTwo(), player);
        }
    }

    public static void generateClaimPillars(Claim claim, Material material, Player player) {
        for (Location corner : getCorners(claim)) {
            generatePillar(material, corner, player);
        }
    }

    public static void resetClaimPillars(Claim claim, Player player) {
        for (Location corner : getCorners(claim)) {
            resetPillar(corner, player);
        }
    }

    public static void resetSelectionPillars(Claiming claiming, Player player) {
        if (claiming.getOne() != null && claiming.getTwo() != null) {
            resetClaimPillars(
                    new Claim(claiming.getFaction(), claiming.getOne(), claiming.getTwo()),
                    player
            );
            return;
        }
        if (claiming.getOne() != null) {
            resetPillar(claiming.getOne(), player);
        }
        if (claiming.getTwo() != null) {
            resetPillar(claiming.getTwo(), player);
        }
    }

    private static List<Location> getCorners(Claim claim) {
        World world = claim.one().getWorld();
        return List.of(
                new Location(world, claim.minX(), 0, claim.minZ()),
                new Location(world, claim.minX(), 0, claim.maxZ()),
                new Location(world, claim.maxX(), 0, claim.minZ()),
                new Location(world, claim.maxX(), 0, claim.maxZ())
        );
    }

    public static void generatePillar(Material material, Location location, Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                World world = location.getWorld();
                if (world == null) {
                    return;
                }

                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    Location pillarLocation = new Location(
                            world,
                            location.getBlockX(),
                            y,
                            location.getBlockZ()
                    );
                    if (!world.getBlockAt(pillarLocation).isEmpty()) {
                        continue;
                    }

                    Material pillarMaterial = y % 4 == 0 ? material : Material.GLASS;
                    player.sendBlockChange(pillarLocation, pillarMaterial.createBlockData());
                }
            }
        }.runTaskLater(Main.getInstance(), 1);
    }

    public static void resetPillar(Location location, Player player) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }

        for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
            Location pillarLocation = new Location(
                    world,
                    location.getBlockX(),
                    y,
                    location.getBlockZ()
            );
            if (!world.getBlockAt(pillarLocation).isEmpty()) {
                continue;
            }
            player.sendBlockChange(pillarLocation, Material.AIR.createBlockData());
        }
    }
}
