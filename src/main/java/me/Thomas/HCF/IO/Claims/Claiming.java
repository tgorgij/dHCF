package me.Thomas.HCF.IO.Claims;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Claiming {

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

    private Material getRandomPillar() {
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
}
