package me.Thomas.HCF.Events.Player.Interact;

import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Subclaim.Subclaim;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Objects;

public final class UserSubclaimEvent implements Listener {
    private final Subclaim subclaims = new Subclaim();

    private void message(Player player, String key) {
        player.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.subclaim." + key)));
    }

    private boolean denied(Player player, Block block) {
        if (User.get(player).isBypass()) return false;
        boolean denied = subclaims.getOwners(block).stream().anyMatch(owner -> !owner.getUUID().equals(player.getUniqueId()));
        if (denied) message(player, "protected");
        return denied;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        if (!(event.getBlock().getState() instanceof Sign sign)) return;
        Player player = event.getPlayer();
        if (denied(player, event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        Side previousSide = subclaims.getSide(sign);
        if (!Subclaim.isMarker(event.line(0))) {
            if (previousSide == event.getSide()) {
                subclaims.clear(sign);
                sign.update();
            }
            return;
        }
        if (previousSide != null && previousSide != event.getSide()
                && Subclaim.isMarker(sign.getSide(previousSide).line(0))) {
            message(player, "one-owner");
            event.setCancelled(true);
            return;
        }
        Block storage = subclaims.getAttachedStorage(event.getBlock());
        if (storage == null) {
            message(player, "invalid-container");
            event.setCancelled(true);
            return;
        }
        User creator = User.get(player);
        Faction faction = Faction.getAtLocation(storage.getLocation());
        Faction signFaction = Faction.getAtLocation(event.getBlock().getLocation());
        if (faction == null || faction.getType() != Faction.Type.PLAYER || signFaction == null
                || !faction.getUUID().equals(signFaction.getUUID())
                || !Objects.equals(creator.getFaction(), faction.getUUID()) || !faction.hasMember(creator.getUUID())) {
            message(player, "own-claim");
            event.setCancelled(true);
            return;
        }
        String name = PlainTextComponentSerializer.plainText().serialize(Objects.requireNonNull(event.line(1))).trim();
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            message(player, "one-owner");
            event.setCancelled(true);
            return;
        }
        User owner = User.get(name);
        // A double chest is one container; a second sign cannot grant conflicting ownership.
        // Reassigning this sign is allowed by its owner; other signs cannot grant conflicting ownership.
        boolean conflict = subclaims.getStorageBlocks(storage).stream()
                .flatMap(block -> subclaims.getSigns(block).stream())
                .filter(other -> !other.getBlock().equals(sign.getBlock()))
                .map(subclaims::getActiveOwner)
                .filter(Objects::nonNull)
                .anyMatch(existing -> owner == null || !existing.getUUID().equals(owner.getUUID()));
        if (conflict) {
            message(player, "conflict");
            event.setCancelled(true);
            return;
        }
        subclaims.assign(sign, event.getSide(), faction, owner == null ? name : owner.getName(), owner);
        if (!sign.update()) {
            event.setCancelled(true);
            message(player, "save-failed");
            return;
        }
        event.line(0, Component.text(Subclaim.MARKER, NamedTextColor.YELLOW));
        event.line(1, Component.text(owner == null ? name : owner.getName()));
        if (owner != null && faction.hasMember(owner.getUUID()) && faction.getUUID().equals(owner.getFaction()) && !faction.isRaidable()
                && storage.getState() instanceof org.bukkit.inventory.InventoryHolder holder) {
            for (var viewer : java.util.List.copyOf(holder.getInventory().getViewers())) {
                if (viewer instanceof Player other && !other.getUniqueId().equals(owner.getUUID()) && !User.get(other).isBypass()) {
                    other.closeInventory();
                }
            }
        }
        message(player, "created");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (denied(event.getPlayer(), event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Block attached = subclaims.getAttachedStorage(block);
        if (denied(event.getPlayer(), block) || (attached != null && denied(event.getPlayer(), attached))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        // Also blocks dye, glow ink, wax, and the sign editor on either side.
        if (denied(event.getPlayer(), event.getClickedBlock())) {
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignOpen(PlayerOpenSignEvent event) {
        if (denied(event.getPlayer(), event.getSign().getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        for (Block block : subclaims.getInventoryBlocks(event.getInventory().getHolder())) {
            if (denied(player, block)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoveItem(InventoryMoveItemEvent event) {
        boolean protectedStorage = subclaims.getInventoryBlocks(event.getSource().getHolder()).stream()
                .anyMatch(block -> !subclaims.getOwners(block).isEmpty())
                || subclaims.getInventoryBlocks(event.getDestination().getHolder()).stream()
                .anyMatch(block -> !subclaims.getOwners(block).isEmpty());
        if (protectedStorage) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> !subclaims.getOwners(block).isEmpty());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> !subclaims.getOwners(block).isEmpty());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (!subclaims.getOwners(event.getBlock()).isEmpty()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> !subclaims.getOwners(block).isEmpty())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> !subclaims.getOwners(block).isEmpty())) event.setCancelled(true);
    }
}
