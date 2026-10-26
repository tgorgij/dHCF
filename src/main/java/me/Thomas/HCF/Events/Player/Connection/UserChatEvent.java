package me.Thomas.HCF.Events.Player.Connection;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UserChatEvent implements Listener {
    private static final Map<UUID, UUID> factionChat = new ConcurrentHashMap<>();

    public static void setFactionChat(UUID player, boolean enabled) {
        if (enabled) factionChat.put(player, UUID.randomUUID());
        else factionChat.remove(player);
    }

    /** Called on the server thread by commands and the scheduled chat handler. */
    public static void sendFactionMessage(Player player, String message) {
        User user = User.get(player);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction == null || !faction.hasMember(player.getUniqueId())) {
            setFactionChat(player.getUniqueId(), false);
            player.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.not-in")));
            return;
        }
        String format = Main.getInstance().getLocaleConfig().getString("commands.faction.chat.format");
        Component formatted = C.chat(format.replace("%player%", player.getName()).replace("%faction%", faction.getName()))
                .replaceText(builder -> builder.matchLiteral("%message%").replacement(Component.text(message)));
        for (UUID uuid : faction.getOnlineMembers().keySet()) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) member.sendMessage(formatted);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        UUID session = factionChat.get(e.getPlayer().getUniqueId());
        if (session == null) return;
        e.setCancelled(true);
        Player player = e.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(e.message());
        // Faction membership and Bukkit state are only accessed on the server thread.
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            if (player.isOnline() && session.equals(factionChat.get(player.getUniqueId()))) {
                sendFactionMessage(player, message);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        setFactionChat(e.getPlayer().getUniqueId(), false);
    }
}
