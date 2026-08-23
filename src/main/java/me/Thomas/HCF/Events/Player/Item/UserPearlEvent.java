package me.Thomas.HCF.Events.Player.Item;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Objects;

public class UserPearlEvent implements Listener {

    @EventHandler
    public void onPearl(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action a = e.getAction();
        if (e.getItem() == null) return;
        if (!e.getItem().getType().equals(Material.ENDER_PEARL)) return;
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        User user = User.get(p);
        if (!user.hasTimer(Timer.Type.ENDERPEARL)) {
            user.addTimer(Timer.Type.ENDERPEARL, new Timer(Timer.Type.ENDERPEARL, true));
            return;
        }
        e.setCancelled(true);
        // todo: make more than default
        p.sendMessage(C.chat(Objects.requireNonNull(Main.getInstance().getLocaleConfig().getString("events.player.timer.ongoing.default"))
                .replace("%timer%", "enderpearl")
                .replace("%time%", C.formatChatTime(user.getTimer(Timer.Type.ENDERPEARL).getRemainingTime()))));
    }
}
