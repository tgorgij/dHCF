package me.Thomas.HCF.Events.Player.Connection;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.ChatEvent;
import me.Thomas.HCF.Commands.Staff.Chat.ChatCommand;
import me.Thomas.HCF.Commands.Staff.StaffChatCommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Utils.Timer.Timer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class UserChatEvent implements Listener {
    private static final Map<UUID, UUID> factionChat = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> allyChat = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> staffChat = new ConcurrentHashMap<>();

    public static boolean isStaffChat(UUID player) { return staffChat.containsKey(player); }

    public static void setStaffChat(UUID player, boolean enabled) {
        if (enabled) {
            factionChat.remove(player);
            allyChat.remove(player);
            staffChat.put(player, UUID.randomUUID());
        } else staffChat.remove(player);
    }

    public static void setFactionChat(UUID player, boolean enabled) {
        if (enabled) {
            staffChat.remove(player);
            allyChat.remove(player);
            factionChat.put(player, UUID.randomUUID());
        } else factionChat.remove(player);
    }

    public static void setAllyChat(UUID player, boolean enabled) {
        if (enabled) {
            staffChat.remove(player);
            factionChat.remove(player);
            allyChat.put(player, UUID.randomUUID());
        } else allyChat.remove(player);
    }

    public static void sendAllyMessage(Player player, String message) {
        User user = User.get(player);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction == null || !faction.hasMember(player.getUniqueId())) {
            setAllyChat(player.getUniqueId(), false);
            player.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.not-in")));
            return;
        }
        Component formatted = C.chat(Main.getInstance().getLocaleConfig().getString("commands.faction.chat.ally-format")
                .replace("%player%", player.getName()).replace("%faction%", faction.getName()))
                .replaceText(builder -> builder.matchLiteral("%message%").replacement(Component.text(message)));
        Set<UUID> recipients = new HashSet<>(faction.getOnlineMembers().keySet());
        for (UUID uuid : faction.getAllies()) recipients.addAll(Faction.get(uuid).getOnlineMembers().keySet());
        for (UUID uuid : recipients) {
            Player member = Bukkit.getPlayer(uuid);
            if (member != null) member.sendMessage(formatted);
        }
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
        UUID staffSession = staffChat.get(e.getPlayer().getUniqueId());
        UUID allySession = staffSession == null ? allyChat.get(e.getPlayer().getUniqueId()) : null;
        Map<UUID, UUID> channel = staffSession != null ? staffChat : allySession != null ? allyChat : factionChat;
        UUID session = staffSession != null ? staffSession : allySession != null ? allySession : factionChat.get(e.getPlayer().getUniqueId());
        if (session == null) return;
        e.setCancelled(true);
        Player player = e.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(e.message());
        // Channel permissions, faction membership, and Bukkit state are only accessed on the server thread.
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            if (player.isOnline() && session.equals(channel.get(player.getUniqueId()))) {
                if (staffSession != null) StaffChatCommand.send(player, message);
                else if (allySession != null) sendAllyMessage(player, message);
                else sendFactionMessage(player, message);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        setFactionChat(e.getPlayer().getUniqueId(), false);
        setStaffChat(e.getPlayer().getUniqueId(), false);
        setAllyChat(e.getPlayer().getUniqueId(), false);
    }

    // User timers and permission checks run on the server thread.
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPublicChat(ChatEvent e) {
        Player p = e.getPlayer();
        if (isStaffChat(p.getUniqueId())) {
            e.setCancelled(true);
            StaffChatCommand.send(p, PlainTextComponentSerializer.plainText().serialize(e.message()));
            return;
        }
        if (allyChat.containsKey(p.getUniqueId())) {
            e.setCancelled(true);
            sendAllyMessage(p, PlainTextComponentSerializer.plainText().serialize(e.message()));
            return;
        }
        if (factionChat.containsKey(p.getUniqueId())) return;
        e.viewers().removeIf(viewer -> viewer instanceof Player recipient && !User.get(recipient).isPublicChat());
        User source = User.get(p);
        String format = Main.getInstance().getLocaleConfig().getString("events.player.chat.format", "&e[%faction%&e] %player%&7: &f%message%");
        Map<UUID, Component> formats = new HashMap<>();
        for (Audience viewer : e.viewers()) {
            if (viewer instanceof Player recipient) formats.put(recipient.getUniqueId(), publicFormat(format, User.get(recipient), source));
        }
        Map<UUID, Component> viewerFormats = Map.copyOf(formats);
        Component consoleFormat = publicFormat(format, null, source);
        // Capture relations on the server thread; rendering only uses immutable components.
        e.renderer((sender, displayName, message, viewer) -> {
            Component prefix = viewer instanceof Player recipient ? viewerFormats.getOrDefault(recipient.getUniqueId(), consoleFormat) : consoleFormat;
            Component body = Component.text(PlainTextComponentSerializer.plainText().serialize(message), NamedTextColor.WHITE);
            return prefix.replaceText(builder -> builder.matchLiteral("%message%").replacement(body));
        });
        if (Permission.has(p, "chat.bypass")) return;
        if (ChatCommand.isPaused()) {
            e.setCancelled(true);
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.chat.paused")));
            return;
        }
        if (ChatCommand.getSlowSeconds() == 0) return;
        User user = User.get(p);
        if (!user.hasTimer(Timer.Type.CHAT)) return;
        e.setCancelled(true);
        long seconds = (user.getTimer(Timer.Type.CHAT).getRemainingTimeMillis() + 999L) / 1000L;
        p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.chat.slow")
                .replace("%time%", Long.toString(seconds))));
    }

    private static Component publicFormat(String format, User viewer, User source) {
        return C.chat(format.replace("%faction%", C.getFactionName(viewer, source))
                .replace("%player%", C.getPlayerColor(viewer, source) + source.getName()));
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPublicChatSent(ChatEvent e) {
        Player p = e.getPlayer();
        if (ChatCommand.getSlowSeconds() == 0 || isStaffChat(p.getUniqueId()) || allyChat.containsKey(p.getUniqueId()) || factionChat.containsKey(p.getUniqueId())
                || Permission.has(p, "chat.bypass")) return;
        User.get(p).addTimer(Timer.Type.CHAT, new Timer(Timer.Type.CHAT,
                System.currentTimeMillis() + ChatCommand.getSlowSeconds() * 1000L, 0L, false));
    }
}
