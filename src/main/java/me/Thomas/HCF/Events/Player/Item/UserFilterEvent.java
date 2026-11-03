package me.Thomas.HCF.Events.Player.Item;

import me.Thomas.HCF.IO.User.User;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;

public class UserFilterEvent implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && User.get(p).isItemFiltered(e.getItem().getItemStack().getType())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArrowPickup(PlayerPickupArrowEvent e) {
        if (User.get(e.getPlayer()).isItemFiltered(e.getItem().getItemStack().getType())) e.setCancelled(true);
    }
}
