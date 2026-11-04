package me.Thomas.HCF.Events.Player.Item;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;


public class UserAppleEvent implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        Timer.Type type = getTimerType(e.getItem().getType());
        if (type == null || type.getLength() <= 0) return;
        User user = User.get(e.getPlayer());
        if (!user.hasTimer(type)) return;

        e.setCancelled(true);
        long seconds = (user.getTimer(type).getRemainingTimeMillis() + 999L) / 1000L;
        e.getPlayer().sendMessage(C.chat(Main.getInstance().getLocaleConfig()
                        .getTimerMessage("ongoing", type)
                .replace("%time%", C.formatChatTime(Math.max(1L, seconds)))));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsumed(PlayerItemConsumeEvent e) {
        Timer.Type type = getTimerType(e.getItem().getType());
        if (type == null || type.getLength() <= 0) return;
        // Start only after consumption has passed every cancellation check.
        User.get(e.getPlayer()).addTimer(type, new Timer(type, true));
    }

    private Timer.Type getTimerType(Material material) {
        return switch (material) {
            case GOLDEN_APPLE -> Timer.Type.APPLE;
            case ENCHANTED_GOLDEN_APPLE -> Timer.Type.GAPPLE;
            default -> null;
        };
    }
}
