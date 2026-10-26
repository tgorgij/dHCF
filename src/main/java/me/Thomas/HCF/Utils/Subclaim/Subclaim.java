package me.Thomas.HCF.Utils.Subclaim;

import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Subclaim ownership is stored on the sign; faction membership is checked at each access. */
public final class Subclaim {
    public static final String MARKER = "[Subclaim]";
    private final NamespacedKey factionKey = new NamespacedKey(Main.getInstance(), "subclaim-faction");
    private final NamespacedKey ownerKey = new NamespacedKey(Main.getInstance(), "subclaim-owner");
    private final NamespacedKey nameKey = new NamespacedKey(Main.getInstance(), "subclaim-name");
    private final NamespacedKey sideKey = new NamespacedKey(Main.getInstance(), "subclaim-side");

    public static boolean isStorage(Material material) {
        return material == Material.CHEST || material == Material.TRAPPED_CHEST || material == Material.BARREL;
    }

    public static boolean isMarker(net.kyori.adventure.text.Component line) {
        return line != null && MARKER.equalsIgnoreCase(PlainTextComponentSerializer.plainText().serialize(line).trim());
    }

    public Side getSide(Sign sign) {
        String side = sign.getPersistentDataContainer().get(sideKey, PersistentDataType.STRING);
        if (side == null) return null;
        try {
            return Side.valueOf(side);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public void assign(Sign sign, Side side, Faction faction, String name, User owner) {
        var data = sign.getPersistentDataContainer();
        data.set(factionKey, PersistentDataType.STRING, faction.getUUID().toString());
        data.set(nameKey, PersistentDataType.STRING, name);
        data.set(sideKey, PersistentDataType.STRING, side.name());
        if (owner == null) data.remove(ownerKey);
        else data.set(ownerKey, PersistentDataType.STRING, owner.getUUID().toString());
    }

    public void clear(Sign sign) {
        var data = sign.getPersistentDataContainer();
        data.remove(factionKey);
        data.remove(ownerKey);
        data.remove(nameKey);
        data.remove(sideKey);
    }

    public Block getAttachedStorage(Block sign) {
        Block attached;
        if (sign.getBlockData() instanceof org.bukkit.block.data.type.WallSign wallSign) {
            attached = sign.getRelative(wallSign.getFacing().getOppositeFace());
        } else if (sign.getBlockData() instanceof org.bukkit.block.data.type.Sign) {
            attached = sign.getRelative(BlockFace.DOWN);
        } else {
            return null;
        }
        return isStorage(attached.getType()) ? attached : null;
    }

    public List<Sign> getSigns(Block storage) {
        List<Sign> signs = new ArrayList<>();
        for (BlockFace face : List.of(BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            Block neighbor = storage.getRelative(face);
            if (neighbor.getState() instanceof Sign sign && storage.equals(getAttachedStorage(neighbor))) signs.add(sign);
        }
        return signs;
    }

    public User getActiveOwner(Sign sign) {
        Block storage = getAttachedStorage(sign.getBlock());
        if (storage == null) return null;
        Side side = getSide(sign);
        if (side == null || !isMarker(sign.getSide(side).line(0))) return null;
        Faction faction = Faction.getAtLocation(storage.getLocation());
        if (faction == null || faction.getType() != Faction.Type.PLAYER || faction.isRaidable()) return null;
        String factionId = sign.getPersistentDataContainer().get(factionKey, PersistentDataType.STRING);
        if (!faction.getUUID().toString().equals(factionId)) return null;
        String ownerId = sign.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        User owner;
        if (ownerId != null) {
            try {
                owner = Main.getInstance().getAllUsers().get(UUID.fromString(ownerId));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        } else {
            String name = sign.getPersistentDataContainer().get(nameKey, PersistentDataType.STRING);
            owner = name == null ? null : User.get(name);
        }
        return owner != null && faction.getUUID().equals(owner.getFaction()) && faction.hasMember(owner.getUUID()) ? owner : null;
    }

    public List<Block> getStorageBlocks(Block storage) {
        if (!isStorage(storage.getType())) return List.of();
        BlockState state = storage.getState();
        if (state instanceof Chest chest && chest.getInventory().getHolder() instanceof DoubleChest doubleChest) {
            List<Block> blocks = new ArrayList<>();
            if (doubleChest.getLeftSide() instanceof Chest left) blocks.add(left.getBlock());
            if (doubleChest.getRightSide() instanceof Chest right) blocks.add(right.getBlock());
            if (!blocks.isEmpty()) return blocks;
        }
        return List.of(storage);
    }

    public List<User> getOwners(Block block) {
        if (block.getState() instanceof Sign sign) {
            User owner = getActiveOwner(sign);
            return owner == null ? List.of() : List.of(owner);
        }
        List<User> owners = new ArrayList<>();
        for (Block storage : getStorageBlocks(block)) {
            for (Sign sign : getSigns(storage)) {
                User owner = getActiveOwner(sign);
                if (owner != null) owners.add(owner);
            }
        }
        return owners;
    }

    public List<Block> getInventoryBlocks(InventoryHolder holder) {
        if (holder instanceof DoubleChest chest) {
            List<Block> blocks = new ArrayList<>();
            if (chest.getLeftSide() instanceof Chest left) blocks.add(left.getBlock());
            if (chest.getRightSide() instanceof Chest right) blocks.add(right.getBlock());
            return blocks;
        }
        if (holder instanceof BlockState state && isStorage(state.getType())) return getStorageBlocks(state.getBlock());
        return List.of();
    }
}
