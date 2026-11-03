package me.Thomas.HCF.Events.Player.Interact;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import io.papermc.paper.event.block.PlayerShearBlockEvent;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.DTC.DTC;
import me.Thomas.HCF.IO.Mountain.Mountain;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class UserInteractInFactionEvent implements Listener {

    private static final long MESSAGE_COOLDOWN_MILLIS = 1_000L;

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();
    private final Map<UUID, Long> pressurePlateMessageCooldowns = new HashMap<>();
    private final Set<Material> guiBlocks = EnumSet.of(
            Material.ANVIL,
            Material.BEACON,
            Material.CARTOGRAPHY_TABLE,
            Material.CHIPPED_ANVIL,
            Material.CRAFTING_TABLE,
            Material.DAMAGED_ANVIL,
            Material.ENCHANTING_TABLE,
            Material.ENDER_CHEST,
            Material.GRINDSTONE,
            Material.LOOM,
            Material.SMITHING_TABLE,
            Material.STONECUTTER
    );

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Location location = e.getBlock().getLocation();
        if (disallowed(p, location, "build")) e.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (DTC.get() != null && DTC.get().contains(e.getBlock().getLocation())) return;
        Player p = e.getPlayer();
        Location location = e.getBlock().getLocation();
        Faction faction = Faction.getAtLocation(location);
        if (faction != null && faction.getType() == Faction.Type.MOUNTAIN && !User.get(p).isBypass()) {
            Mountain mountain = Mountain.get(faction.getUUID());
            if (mountain == null || !mountain.contains(location) || User.get(p).hasPvpProtection()) {
                e.setCancelled(true);
                p.sendMessage(C.chat(localeConfig.getString("faction.modify.build")
                        .replace("%faction%", faction.getFormattedName(User.get(p)))));
            }
            return;
        }
        if (disallowed(p, location, "build")) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Block block = e.getClickedBlock();
        if (block == null) return;

        if (e.getAction() == Action.PHYSICAL && Tag.PRESSURE_PLATES.isTagged(block.getType())) {
            if (disallowed(e.getPlayer(), block.getLocation(), "interact", true)) {
                e.setUseInteractedBlock(Event.Result.DENY);
            }
            return;
        }

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        boolean protectedInteraction = block.getState() instanceof InventoryHolder
                || block.getBlockData() instanceof Openable
                || block.getBlockData() instanceof Switch
                || guiBlocks.contains(block.getType());
        if (protectedInteraction && disallowed(e.getPlayer(), block.getLocation(), "interact")) {
            e.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (disallowed(e.getPlayer(), e.getBlock().getLocation(), "build")) e.setCancelled(true);
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (disallowed(e.getPlayer(), e.getBlock().getLocation(), "build")) e.setCancelled(true);
    }

    @EventHandler
    public void onFertilize(BlockFertilizeEvent e) {
        Player p = e.getPlayer();
        if (p != null && disallowed(p, e.getBlock().getLocation(), "interact")) e.setCancelled(true);
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent e) {
        Player p = e.getPlayer();
        if (p != null && disallowed(p, e.getBlock().getLocation(), "interact")) e.setCancelled(true);
    }

    @EventHandler
    public void onStructureGrow(StructureGrowEvent e) {
        Player p = e.getPlayer();
        if (p != null && disallowed(p, e.getLocation(), "interact")) e.setCancelled(true);
    }

    @EventHandler
    public void onHangingPlace(HangingPlaceEvent e) {
        Player p = e.getPlayer();
        if (p != null && disallowed(p, e.getEntity().getLocation(), "build")) e.setCancelled(true);
    }

    @EventHandler
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Entity remover = e.getRemover();
        if (remover instanceof Player p && disallowed(p, e.getEntity().getLocation(), "interact")) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityPlace(EntityPlaceEvent e) {
        Player p = e.getPlayer();
        if (p != null && disallowed(p, e.getEntity().getLocation(), "interact")) e.setCancelled(true);
    }

    @EventHandler
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
        if (disallowed(e.getPlayer(), e.getRightClicked().getLocation(), "interact")) e.setCancelled(true);
    }

    @EventHandler
    public void onArmorStandDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof ArmorStand) || !(e.getDamager() instanceof Player p)) return;
        if (disallowed(p, e.getEntity().getLocation(), "interact")) e.setCancelled(true);
    }

    @EventHandler
    public void onBed(PlayerBedEnterEvent e) {
        if (disallowed(e.getPlayer(), e.getBed().getLocation(), "interact")) {
            e.setUseBed(Event.Result.DENY);
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onSetSpawn(PlayerSetSpawnEvent e) {
        if (e.getLocation() == null) return;
        e.setNotifyPlayer(false);
        e.setCancelled(true);
    }

    @EventHandler
    public void onShear(PlayerShearBlockEvent e) {
        if (disallowed(e.getPlayer(), e.getBlock().getLocation(), "interact")) e.setCancelled(true);
    }

    @EventHandler
    public void onShearEntity(PlayerShearEntityEvent e) {
        if (disallowed(e.getPlayer(), e.getEntity().getLocation(), "interact")) e.setCancelled(true);
    }

    @EventHandler
    public void onLeash(PlayerLeashEntityEvent e) {
        if (disallowed(e.getPlayer(), e.getEntity().getLocation(), "interact")) e.setCancelled(true);
    }

    @EventHandler
    public void onUnleash(PlayerUnleashEntityEvent e) {
        if (disallowed(e.getPlayer(), e.getEntity().getLocation(), "interact")) e.setCancelled(true);
    }

    @EventHandler
    public void onTakeLecternBook(PlayerTakeLecternBookEvent e) {
        if (disallowed(e.getPlayer(), e.getLectern().getLocation(), "interact")) e.setCancelled(true);
    }

    private boolean disallowed(Player p, Location location, String message) {
        return disallowed(p, location, message, false);
    }

    private boolean disallowed(Player p, Location location, String message, boolean throttleMessage) {
        User user = User.get(p);
        if (user.isBypass()) return false;
        Faction faction = Faction.getAtLocation(location);
        boolean disallowed = faction == null
                ? Faction.isInWarzoneBuildLimit(location)
                : faction.getType() == Faction.Type.MOUNTAIN || !user.hasFaction() || !user.getFaction().equals(faction.getUUID());
        if (faction != null && faction.getType().equals(Faction.Type.PLAYER) && faction.getDTR() <= 0) return false;
        if (disallowed && (!throttleMessage || canSendPressurePlateMessage(p))) {
            String messageFormat = "faction.modify.";
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString(messageFormat + message))
                    .replace("%faction%", Faction.getNameAtLocation(location, user))));
        }
        return disallowed;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        pressurePlateMessageCooldowns.remove(e.getPlayer().getUniqueId());
    }

    private boolean canSendPressurePlateMessage(Player p) {
        long now = System.currentTimeMillis();
        long lastMessage = pressurePlateMessageCooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (now - lastMessage < MESSAGE_COOLDOWN_MILLIS) return false;
        pressurePlateMessageCooldowns.put(p.getUniqueId(), now);
        return true;
    }
}
