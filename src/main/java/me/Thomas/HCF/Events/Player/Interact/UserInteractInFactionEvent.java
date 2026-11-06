package me.Thomas.HCF.Events.Player.Interact;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import io.papermc.paper.event.block.PlayerShearBlockEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import io.papermc.paper.event.player.PlayerFlowerPotManipulateEvent;
import io.papermc.paper.event.player.PlayerOpenSignEvent;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.DTC.DTC;
import me.Thomas.HCF.IO.Mountain.Mountain;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.damage.DamageSource;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.block.CauldronLevelChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.InventoryHolder;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class UserInteractInFactionEvent implements Listener {

    private static final long MESSAGE_COOLDOWN_MILLIS = 1_000L;

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();
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
        if (e instanceof BlockMultiPlaceEvent multi) {
            for (BlockState state : multi.getReplacedBlockStates()) {
                if (disallowed(p, state.getLocation(), "build")) {
                    e.setCancelled(true);
                    return;
                }
            }
        } else if (disallowed(p, e.getBlock().getLocation(), "build")) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (Main.getInstance().isFeatureEnabled("dtc") && DTC.get() != null && DTC.get().contains(e.getBlock().getLocation())) return;
        Player p = e.getPlayer();
        Location location = e.getBlock().getLocation();
        Faction faction = Faction.getAtLocation(location);
        if (Main.getInstance().isFeatureEnabled("mountain") && faction != null
                && faction.getType() == Faction.Type.MOUNTAIN && !User.get(p).isBypass()) {
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

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        boolean protectedInteraction = block.getType() == Material.RESPAWN_ANCHOR
                || block.getState() instanceof InventoryHolder
                || block.getBlockData() instanceof Openable
                || block.getBlockData() instanceof Switch
                || guiBlocks.contains(block.getType());
        boolean modifiesBlock = modifiesBlock(e.getItem())
                || block.getState() instanceof Sign && e.getItem() != null
                && (e.getItem().getType().name().endsWith("_DYE")
                || e.getItem().getType() == Material.INK_SAC || e.getItem().getType() == Material.GLOW_INK_SAC);
        if ((protectedInteraction || modifiesBlock)
                && disallowed(e.getPlayer(), block.getLocation(), modifiesBlock ? "build" : "interact")) {
            if (protectedInteraction) e.setUseInteractedBlock(Event.Result.DENY);
            if (modifiesBlock) e.setUseItemInHand(Event.Result.DENY);
        }
    }

    private boolean modifiesBlock(ItemStack item) {
        if (item == null) return false;
        Material material = item.getType();
        String name = material.name();
        return name.endsWith("_AXE") || name.endsWith("_HOE") || name.endsWith("_SHOVEL")
                || material == Material.HONEYCOMB || material == Material.BONE_MEAL
                || material == Material.SHEARS || material == Material.BRUSH;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignOpen(PlayerOpenSignEvent e) {
        if (disallowed(e.getPlayer(), e.getSign().getLocation(), "build")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent e) {
        if (disallowed(e.getPlayer(), e.getBlock().getLocation(), "build")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCauldronChange(CauldronLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && disallowed(p, e.getBlock().getLocation(), "build")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent e) {
        if (disallowed(e.getPlayer(), e.getHarvestedBlock().getLocation(), "build")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlowerPot(PlayerFlowerPotManipulateEvent e) {
        if (disallowed(e.getPlayer(), e.getFlowerpot().getLocation(), "build")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChangeBlock(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof Player p && disallowed(p, e.getBlock().getLocation(), "build")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        InventoryHolder holder = e.getInventory().getHolder();
        if (holder instanceof Player || !(holder instanceof Entity || holder instanceof BlockState || holder instanceof DoubleChest)) return;
        for (Location location : inventoryLocations(e.getInventory())) {
            if (disallowed(p, location, "interact")) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private NamespacedKey hopperFactionKey() {
        return new NamespacedKey(Main.getInstance(), "hopper-faction");
    }

    private boolean protectedFaction(Faction faction) {
        return faction != null && (faction.getType() != Faction.Type.PLAYER
                || !faction.isRaidable() && !Main.getInstance().isEOTWOngoing());
    }

    private List<Location> inventoryLocations(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof DoubleChest chest) {
            List<Location> locations = new ArrayList<>();
            if (chest.getLeftSide() instanceof BlockState left) locations.add(left.getLocation());
            if (chest.getRightSide() instanceof BlockState right) locations.add(right.getLocation());
            return locations;
        }
        Location location = holder instanceof Entity entity ? entity.getLocation()
                : holder instanceof BlockState block ? block.getLocation() : inventory.getLocation();
        return location == null ? List.of() : List.of(location);
    }

    private boolean ownsHopper(InventoryHolder holder, Faction faction) {
        return !(holder instanceof HopperMinecart cart) || faction.getUUID().toString().equals(
                cart.getPersistentDataContainer().get(hopperFactionKey(), PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHopperPlace(EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof HopperMinecart cart)) return;
        cart.getPersistentDataContainer().remove(hopperFactionKey());
        Player p = e.getPlayer();
        if (p == null) return;
        User user = User.get(p);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction != null && faction.getType() == Faction.Type.PLAYER && faction.hasMember(p.getUniqueId())) {
            cart.getPersistentDataContainer().set(hopperFactionKey(), PersistentDataType.STRING, faction.getUUID().toString());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent e) {
        InventoryHolder source = e.getSource().getHolder(), destination = e.getDestination().getHolder();
        boolean hopperCart = source instanceof HopperMinecart || destination instanceof HopperMinecart;
        List<Location> initiator = hopperCart ? List.of() : inventoryLocations(e.getInitiator());
        List<Location> locations = new ArrayList<>(inventoryLocations(e.getSource()));
        locations.addAll(inventoryLocations(e.getDestination()));
        for (Location location : locations) {
            Faction faction = Faction.getAtLocation(location);
            if (!protectedFaction(faction)) continue;
            boolean allowed = hopperCart ? ownsHopper(source, faction) && ownsHopper(destination, faction)
                    : !initiator.isEmpty() && initiator.stream().allMatch(origin -> {
                        Faction owner = Faction.getAtLocation(origin);
                        return owner != null && owner.getUUID().equals(faction.getUUID());
                    });
            if (!allowed) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemFrameChange(PlayerItemFrameChangeEvent e) {
        if (disallowed(e.getPlayer(), e.getItemFrame().getLocation(), "interact")) e.setCancelled(true);
    }

    private boolean vehicleDamageDenied(Vehicle vehicle, Entity attacker, DamageSource damage) {
        if (!protectedFaction(Faction.getAtLocation(vehicle.getLocation()))) return false;
        Entity direct = damage.getDirectEntity();
        if (attacker instanceof Player p && (direct == null || direct instanceof Player)) {
            return disallowed(p, vehicle.getLocation(), "interact");
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (e.getEntered() instanceof Player p
                && protectedFaction(Faction.getAtLocation(e.getVehicle().getLocation()))
                && disallowed(p, e.getVehicle().getLocation(), "interact")) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;
        Entity caught = e.getCaught();
        if ((caught instanceof Vehicle || caught instanceof ArmorStand)
                && protectedFaction(Faction.getAtLocation(caught.getLocation()))
                && disallowed(e.getPlayer(), caught.getLocation(), "interact")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDamage(VehicleDamageEvent e) {
        if (vehicleDamageDenied(e.getVehicle(), e.getAttacker(), e.getDamageSource())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent e) {
        if (vehicleDamageDenied(e.getVehicle(), e.getAttacker(), e.getDamageSource())) e.setCancelled(true);
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTNTPrime(TNTPrimeEvent e) {
        Player player = e.getPrimingEntity() instanceof Player p ? p
                : e.getPrimingEntity() instanceof Projectile projectile && projectile.getShooter() instanceof Player p ? p : null;
        if (player != null && disallowed(player, e.getBlock().getLocation(), "build")) e.setCancelled(true);
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
        if (faction != null && faction.getType() == Faction.Type.PLAYER
                && (faction.getDTR() <= 0 || Main.getInstance().isEOTWOngoing())) return false;
        if (disallowed && (!throttleMessage || canSendPressurePlateMessage(p))) {
            String messageFormat = "faction.modify.";
            p.sendMessage(C.chat(localeConfig.getString(messageFormat + message)
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
