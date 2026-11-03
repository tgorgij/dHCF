package me.Thomas.HCF.Events.Player.Interact;

import me.Thomas.HCF.IO.DTC.DTC;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;

public class UserDTCEvent implements Listener {
    private boolean core(Block block) { return DTC.get() != null && DTC.get().contains(block.getLocation()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!core(e.getBlock())) return;
        if (!DTC.get().canDamage(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.dtc." + (DTC.get().isRunning() ? "cannot-damage" : "inactive"))));
            return;
        }
        e.setDropItems(false);
        e.setExpToDrop(0);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBroken(BlockBreakEvent e) {
        if (!core(e.getBlock())) return;
        DTC.get().breakCore(e.getPlayer());
        // Restore after vanilla removes the block; cancelled breaks never damage the core.
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> DTC.get().restoreCore());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { if (core(e.getBlockPlaced())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) { e.blockList().removeIf(this::core); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (core(e.getBlock())) e.setCancelled(true);
        else e.blockList().removeIf(this::core);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (core(e.getBlock()) || e.getBlocks().stream().anyMatch(block -> core(block) || core(block.getRelative(e.getDirection())))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (core(e.getBlock()) || e.getBlocks().stream().anyMatch(block -> core(block) || core(block.getRelative(e.getDirection())))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() != null && core(e.getClickedBlock()) && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setUseInteractedBlock(Event.Result.DENY);
            e.setUseItemInHand(Event.Result.DENY);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrime(TNTPrimeEvent e) { if (core(e.getBlock())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) { if (core(e.getBlock())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onForm(BlockFormEvent e) { if (core(e.getBlock())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChange(EntityChangeBlockEvent e) { if (core(e.getBlock())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) { if (core(e.getBlock())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) { if (core(e.getBlock())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) { if (core(e.getToBlock())) e.setCancelled(true); }
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        if (DTC.get() != null && DTC.get().isCoreChunk(e.getWorld().getUID(), e.getChunk().getX(), e.getChunk().getZ())) DTC.get().restoreCore();
    }
    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        if (DTC.get() != null && e.getWorld().getUID().equals(DTC.get().getWorld())) DTC.get().restoreCore();
    }
}
