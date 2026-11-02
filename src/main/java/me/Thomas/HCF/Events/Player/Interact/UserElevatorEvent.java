package me.Thomas.HCF.Events.Player.Interact;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.BoundingBox;

public class UserElevatorEvent implements Listener {

    private static String plain(Component line) {
        return line == null ? "" : PlainTextComponentSerializer.plainText().serialize(line).trim();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent e) {
        if (!plain(e.line(0)).equalsIgnoreCase("[Elevator]")) return;
        String direction = plain(e.line(1));
        if (!direction.equalsIgnoreCase("up") && !direction.equalsIgnoreCase("down")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.elevator.invalid-direction")));
            return;
        }
        e.line(0, C.chat("&9[Elevator]"));
        e.line(1, Component.text(direction.equalsIgnoreCase("up") ? "Up" : "Down"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND
                || e.useInteractedBlock() == Event.Result.DENY || e.getClickedBlock() == null
                || !(e.getClickedBlock().getState() instanceof Sign sign)) return;
        Player p = e.getPlayer();
        SignSide side = sign.getTargetSide(p);
        if (!plain(side.line(0)).equalsIgnoreCase("[Elevator]")) return;
        String direction = plain(side.line(1));
        if (!direction.equalsIgnoreCase("up") && !direction.equalsIgnoreCase("down")) return;
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        Location destination = findDestination(e.getClickedBlock(), direction.equalsIgnoreCase("up"), p.getLocation());
        if (destination == null) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.elevator.no-destination")));
            return;
        }
        p.teleport(destination);
    }

    static Location findDestination(Block sign, boolean up, Location origin) {
        World world = sign.getWorld();
        int x = sign.getX(), z = sign.getZ(), step = up ? 1 : -1;
        int y = sign.getY() + step;
        // The block immediately above the starting sign is allowed to be air.
        if (up && y < world.getMaxHeight() && world.getBlockAt(x, y, z).getType().isAir()) y++;
        boolean solid = false;
        for (; y >= world.getMinHeight() && y < world.getMaxHeight(); y += step) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid() && !block.isLiquid()) {
                solid = true;
                continue;
            }
            if (!solid || !block.getType().isAir()) return null;
            int feet = up ? y : y - 1;
            if (feet <= world.getMinHeight() || feet + 1 >= world.getMaxHeight()) return null;
            if (!world.getBlockAt(x, feet, z).getType().isAir()
                    || !world.getBlockAt(x, feet + 1, z).getType().isAir()) return null;
            Block floor = world.getBlockAt(x, feet - 1, z);
            Material type = floor.getType();
            if (!type.isSolid() || floor.isLiquid() || type == Material.MAGMA_BLOCK || type == Material.CACTUS
                    || type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE) return null;
            BoundingBox box = floor.getBoundingBox();
            if (box.getMaxY() != feet || box.getMinX() > x + 0.2 || box.getMaxX() < x + 0.8
                    || box.getMinZ() > z + 0.2 || box.getMaxZ() < z + 0.8) return null;
            Location destination = new Location(world, x + 0.5, feet, z + 0.5, origin.getYaw(), origin.getPitch());
            return world.getWorldBorder().isInside(destination) ? destination : null;
        }
        return null;
    }
}
