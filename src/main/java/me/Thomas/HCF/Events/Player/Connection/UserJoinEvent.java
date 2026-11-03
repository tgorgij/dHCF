package me.Thomas.HCF.Events.Player.Connection;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Deathban.Deathban;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.logging.Level;

public class UserJoinEvent implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onValidateLogin(PlayerConnectionValidateLoginEvent e) {
        if (!e.isAllowed() || !Main.getInstance().isFeatureEnabled("deathbans")) return;
        PlayerProfile profile = null;
        if (e.getConnection() instanceof PlayerLoginConnection connection) {
            profile = connection.getAuthenticatedProfile();
        } else if (e.getConnection() instanceof PlayerConfigurationConnection connection) {
            profile = connection.getProfile();
        }
        if (profile == null || profile.getId() == null) return;

        // Existing users are loaded at startup; avoid creating first-time users before onJoin.
        User user = Main.getInstance().getAllUsers().get(profile.getId());
        if (user == null) return;
        long remaining = Deathban.getRemainingSeconds(user.getDeathbanExpiry(), System.currentTimeMillis());
        if (remaining > 0L && user.getLives() > 0) {
            try {
                user.reviveWithLife();
            } catch (RuntimeException exception) {
                Main.getInstance().getLogger().log(Level.SEVERE, "Could not self-revive player " + user.getUUID(), exception);
                e.kickMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.self-revive-failed")));
                return;
            }
            remaining = Deathban.getRemainingSeconds(user.getDeathbanExpiry(), System.currentTimeMillis());
        }
        if (remaining > 0L) {
            String message = Main.getInstance().getLocaleConfig().getString("events.player.deathban",
                    "&cYou are deathbanned. &eYou can log back in in &c%time%&e.");
            e.kickMessage(C.chat(message.replace("%time%", C.formatChatTime(remaining))));
        } else if (user.getDeathbanExpiry() != 0L) {
            user.setPvpProtectionPending(true);
            user.setDeathbanExpiry(0L);
            user.saveAsync();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boolean firstJoin = !p.hasPlayedBefore();
        if (firstJoin) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.first-join")));
        }
        Main.getInstance().addUser(p);
        User user = User.get(p);
        user.unpauseTimers();
        if (firstJoin && !Main.getInstance().isSOTWOngoing()) user.giveStartingTimer();
        if (p.isDead()) user.setPvpProtectionPending(true);
        // A returning player may still need to close their death screen and respawn.
        if (!p.isDead()) user.applyPendingPvpTimer();
        user.saveAsync();
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction != null) faction.broadcastMemberConnection(p, true);
    }
}
