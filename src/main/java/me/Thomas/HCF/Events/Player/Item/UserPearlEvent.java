package me.Thomas.HCF.Events.Player.Item;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.KOTH.KOTH;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;

public class UserPearlEvent implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLand(PlayerTeleportEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL || e.getTo() == null) return;
        Faction faction = Faction.getAtLocation(e.getTo());
        Faction source = Faction.getAtLocation(e.getFrom());
        if (faction != null && faction.getType() == Faction.Type.CONQUEST
                || source != null && source.getType() == Faction.Type.CONQUEST) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.conquest.pearl-blocked")));
            return;
        }
        if (faction == null) return;
        for (KOTH koth : Main.getInstance().getAllKOTHs().values()) {
            if (koth.isPearlable() || !faction.getUUID().equals(koth.getFaction())) continue;
            e.setCancelled(true);
            e.getPlayer().sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.koth.pearl-blocked")
                    .replace("%koth%", koth.getName())));
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearl(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof EnderPearl pearl) || !(pearl.getShooter() instanceof Player player)) return;
        Faction faction = Faction.getAtLocation(player.getLocation());
        if (faction != null && faction.getType() == Faction.Type.CONQUEST) {
            e.setCancelled(true);
            player.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.conquest.pearl-blocked")));
            return;
        }
        if (Timer.Type.ENDERPEARL.getLength() <= 0) return;
        User user = User.get(player);
        if (!user.hasTimer(Timer.Type.ENDERPEARL)) return;

        e.setCancelled(true);
        long seconds = (user.getTimer(Timer.Type.ENDERPEARL).getRemainingTimeMillis() + 999L) / 1000L;
        player.sendMessage(C.chat(Objects.requireNonNull(Main.getInstance().getLocaleConfig()
                        .getString("events.player.timer.ongoing.enderpearl"))
                .replace("%time%", C.formatChatTime(Math.max(1L, seconds)))));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThrown(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof EnderPearl pearl) || !(pearl.getShooter() instanceof Player player)) return;
        if (Timer.Type.ENDERPEARL.getLength() <= 0) return;
        User.get(player).addTimer(Timer.Type.ENDERPEARL, new Timer(Timer.Type.ENDERPEARL, true));
    }
}
