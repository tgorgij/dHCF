package me.Thomas.HCF.Events.World;

import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.PortalCreateEvent;

import java.util.List;

public class ClaimProtectionEvent implements Listener {

    private boolean protectedClaim(Faction faction) {
        return faction != null && (faction.getType() != Faction.Type.PLAYER
                || !faction.isRaidable() && !Main.getInstance().isEOTWOngoing());
    }

    private boolean protectedClaim(Location location) {
        return protectedClaim(Faction.getAtLocation(location));
    }

    private boolean foreignClaim(Faction source, Location destination) {
        Faction target = Faction.getAtLocation(destination);
        return protectedClaim(target) && (source == null || !source.getUUID().equals(target.getUUID()));
    }

    private boolean pistonBlocked(Block piston, List<Block> blocks, BlockFace direction, boolean extending) {
        Faction source = Faction.getAtLocation(piston.getLocation());
        if (extending && foreignClaim(source, piston.getRelative(direction).getLocation())) return true;
        for (Block block : blocks) {
            if (foreignClaim(source, block.getLocation())
                    || foreignClaim(source, block.getRelative(direction).getLocation())) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent e) {
        if (e.getReason() == PortalCreateEvent.CreateReason.END_PLATFORM) return;
        if (e.getBlocks().stream().anyMatch(block -> Faction.getAtLocation(block.getLocation()) != null)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTNTPrime(TNTPrimeEvent e) {
        if (!protectedClaim(e.getBlock().getLocation())) return;
        if (e.getPrimingEntity() instanceof Player
                || e.getPrimingEntity() instanceof Projectile projectile && projectile.getShooter() instanceof Player) return;
        Block source = e.getPrimingBlock();
        if ((e.getCause() == TNTPrimeEvent.PrimeCause.REDSTONE || e.getCause() == TNTPrimeEvent.PrimeCause.DISPENSER)
                && source != null && !foreignClaim(Faction.getAtLocation(source.getLocation()), e.getBlock().getLocation())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(block -> protectedClaim(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(block -> protectedClaim(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (pistonBlocked(e.getBlock(), e.getBlocks(), e.getDirection(), true)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        // Paper supplies the movement direction for the pulled blocks.
        if (pistonBlocked(e.getBlock(), e.getBlocks(), e.getDirection(), false)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (protectedClaim(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (e.getPlayer() == null && protectedClaim(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent e) {
        Material type = e.getNewState().getType();
        if ((type == Material.FIRE || type == Material.SOUL_FIRE) && protectedClaim(e.getBlock().getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        Material type = e.getBlock().getType();
        if (type != Material.WATER && type != Material.LAVA) return;
        Faction target = Faction.getAtLocation(e.getToBlock().getLocation());
        // Water-outs and other liquid traps remain usable in player claims.
        if (target != null && target.getType() != Faction.Type.PLAYER) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpongeAbsorb(SpongeAbsorbEvent e) {
        Faction source = Faction.getAtLocation(e.getBlock().getLocation());
        e.getBlocks().removeIf(block -> foreignClaim(source, block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof Player || !protectedClaim(e.getBlock().getLocation())) return;
        if (e.getEntity() instanceof FallingBlock) {
            if (foreignClaim(Faction.getAtLocation(e.getEntity().getOrigin()), e.getBlock().getLocation())) e.setCancelled(true);
        } else {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent e) {
        Faction source = Faction.getAtLocation(e.getLocation());
        e.getBlocks().removeIf(block -> foreignClaim(source, block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent e) {
        Faction source = Faction.getAtLocation(e.getBlock().getLocation());
        e.getBlocks().removeIf(block -> foreignClaim(source, block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDecorationDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof ArmorStand) && !(e.getEntity() instanceof Hanging)) return;
        if (!protectedClaim(e.getEntity().getLocation())) return;
        if (e instanceof EntityDamageByEntityEvent damage && damage.getDamager() instanceof Player
                && (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)
                && !(e.getDamageSource().getDirectEntity() instanceof Projectile)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        if (!protectedClaim(e.getEntity().getLocation())) return;
        if (e.getCause() == HangingBreakEvent.RemoveCause.EXPLOSION
                || e instanceof HangingBreakByEntityEvent damage && (!(damage.getRemover() instanceof Player)
                || damage.getDamageSource().getDirectEntity() instanceof Projectile)) {
            e.setCancelled(true);
        }
    }
}
