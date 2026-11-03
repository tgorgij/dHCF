package me.Thomas.HCF.Utils.Tab;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.Action;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate.PlayerInfo;
import me.Thomas.HCF.Events.Player.Movement.UserWorldBorderEvent;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.KOTH.KOTH;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Events.EventScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

public class TabList extends PacketListenerAbstract implements Listener {
    private static final List<UUID> IDS = IntStream.range(0, 80)
            .mapToObj(slot -> UUID.nameUUIDFromBytes(("dhcf-tab-slot-" + slot).getBytes(StandardCharsets.UTF_8))).toList();
    private static final Set<UUID> ID_SET = Set.copyOf(IDS);
    private static final EnumSet<Action> ADD = EnumSet.of(Action.ADD_PLAYER, Action.UPDATE_LISTED, Action.UPDATE_GAME_MODE,
            Action.UPDATE_LATENCY, Action.UPDATE_DISPLAY_NAME, Action.UPDATE_LIST_ORDER, Action.UPDATE_HAT);
    private record Row(int column, int row, String text, String skin) {}
    private record Skin(String value, String signature) {}
    private record Cell(Component text, Skin skin) {}
    private record RankedFaction(Faction faction, int online) {}
    private static class View {
        private final Component originalHeader;
        private final Component originalFooter;
        private List<Cell> cells = List.of();
        private Component header;
        private Component footer;
        private View(Player player) {
            originalHeader = player.playerListHeader();
            originalFooter = player.playerListFooter();
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final Map<String, Skin> textures = new HashMap<>();
    private final Map<UUID, View> views = new HashMap<>();
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final BukkitTask task;

    public TabList() {
        super(PacketListenerPriority.HIGHEST);
        ConfigurationSection skins = Main.getInstance().getTabConfig().getConfigurationSection("skins");
        if (skins != null) for (String skin : skins.getKeys(false)) {
            textures.put(skin, new Skin(skins.getString(skin + ".value", ""), skins.getString(skin + ".signature", "")));
        }
        List<String> columns = List.of("left", "left-middle", "right-middle", "right");
        for (int column = 0; column < columns.size(); column++) {
            ConfigurationSection section = Main.getInstance().getTabConfig().getConfigurationSection("columns." + columns.get(column));
            if (section == null) continue;
            for (String key : section.getKeys(false)) {
                int row;
                try { row = Integer.parseInt(key); }
                catch (NumberFormatException exception) { continue; }
                if (row < 0 || row >= 20) continue;
                rows.add(new Row(column, row, section.getString(key + ".text", ""), section.getString(key + ".skin", "blank")));
            }
        }
        rows.sort(Comparator.comparingInt(Row::column).thenComparingInt(Row::row));
        PacketEvents.getAPI().getEventManager().registerListener(this);
        Bukkit.getPluginManager().registerEvents(this, Main.getInstance());
        for (Player player : Bukkit.getOnlinePlayers()) open(player);
        task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), this::refresh, 1L,
                Math.clamp(Main.getInstance().getTabConfig().getLong("update-ticks", 20L), 2L, 1200L));
    }

    @Override
    public void onPacketSend(PacketSendEvent e) {
        if (e.getPacketType() != PacketType.Play.Server.PLAYER_INFO_UPDATE || !active.contains(e.getUser().getUUID())) return;
        WrapperPlayServerPlayerInfoUpdate packet = new WrapperPlayServerPlayerInfoUpdate(e);
        if (!packet.getActions().contains(Action.ADD_PLAYER) && !packet.getActions().contains(Action.UPDATE_LISTED)) return;
        boolean changed = false;
        for (PlayerInfo entry : packet.getEntries()) {
            if (ID_SET.contains(entry.getProfileId())) continue;
            entry.setListed(false);
            changed = true;
        }
        if (changed) {
            packet.getActions().add(Action.UPDATE_LISTED);
            e.markForReEncode(true);
        }
    }

    private void open(Player player) {
        if (!player.isOnline() || views.containsKey(player.getUniqueId())) return;
        views.put(player.getUniqueId(), new View(player));
        active.add(player.getUniqueId());
        List<PlayerInfo> hidden = Bukkit.getOnlinePlayers().stream().filter(player::canSee).map(other -> {
            PlayerInfo entry = new PlayerInfo(other.getUniqueId());
            entry.setListed(false);
            return entry;
        }).toList();
        if (!hidden.isEmpty()) PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerPlayerInfoUpdate(Action.UPDATE_LISTED, hidden));
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerPlayerInfoRemove(IDS));
    }

    private List<RankedFaction> rankedFactions() {
        return Main.getInstance().getAllFactions().values().stream().filter(faction -> faction.getType() == Faction.Type.PLAYER)
                .map(faction -> new RankedFaction(faction, faction.getOnlineMembers().size()))
                .sorted(Comparator.comparingInt(RankedFaction::online).reversed()
                        .thenComparing(entry -> entry.faction().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(entry -> entry.faction().getUUID())).limit(10).toList();
    }

    private void refresh() {
        Instant now = Instant.now();
        List<RankedFaction> factions = rankedFactions();
        EventScheduler.Upcoming next = Main.getInstance().getEventScheduler().getNext(now);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!views.containsKey(player.getUniqueId())) open(player);
            update(player, factions, next, now);
        }
    }

    private Map<String, String> values(Player player, EventScheduler.Upcoming next, Instant now) {
        User user = User.get(player);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction != null && !faction.hasMember(player.getUniqueId())) faction = null;
        String unset = Main.getInstance().getTabConfig().getString("unset", "-");
        Location location = player.getLocation();
        Location home = faction == null ? null : faction.getHome();
        Map<String, String> values = new HashMap<>();
        values.put("%store%", Main.getInstance().getTabConfig().getString("store", ""));
        values.put("%dtr%", faction == null ? unset : C.getFormattedDTR(faction));
        values.put("%hq%", home == null ? unset : home.getBlockX() + ", " + home.getBlockZ());
        values.put("%faction%", faction == null ? "" : faction.getFormattedName(user));
        values.put("%kills%", Integer.toString(user.getKills()));
        values.put("%deaths%", Integer.toString(user.getDeaths()));
        values.put("%lives%", Integer.toString(user.getLives()));
        values.put("%territory%", Faction.getNameAtLocation(location, user));
        values.put("%x%", Integer.toString(location.getBlockX()));
        values.put("%z%", Integer.toString(location.getBlockZ()));
        values.put("%direction%", direction(location.getYaw()));
        values.put("%nether-border%", Long.toString((long) UserWorldBorderEvent.radius(World.Environment.NETHER)));
        values.put("%overworld-border%", Long.toString((long) UserWorldBorderEvent.radius(World.Environment.NORMAL)));
        values.put("%end-portals%", Main.getInstance().getTabConfig().getString("end-portal-location", "1000"));
        values.put("%online%", Integer.toString(Bukkit.getOnlinePlayers().size()));
        values.put("%max-players%", Integer.toString(Bukkit.getMaxPlayers()));
        values.put("%next-event%", next == null ? Main.getInstance().getTabConfig().getString("no-event", "None") : eventName(next.schedule()));
        values.put("%next-event-time%", next == null ? unset : eventTime(Duration.between(now, next.start()).getSeconds()));
        return values;
    }

    private static String eventName(EventScheduler.Schedule schedule) {
        if (schedule.type() == EventScheduler.Type.KOTH) {
            KOTH koth = KOTH.get(schedule.koth());
            return koth == null ? "" : koth.getName();
        }
        return Main.getInstance().getTabConfig().getString("event-names." + schedule.type().name().toLowerCase(Locale.ROOT), schedule.type().name());
    }

    public static String direction(float yaw) {
        return List.of("S", "SW", "W", "NW", "N", "NE", "E", "SE").get(Math.floorMod(Math.round(yaw / 45.0F), 8));
    }

    public static String eventTime(long seconds) {
        seconds = Math.max(0L, seconds);
        String path = seconds >= 86400L ? "time.days-hours" : "time.hours-minutes";
        return Main.getInstance().getTabConfig().getString(path, seconds >= 86400L ? "%days%d, %hours%h" : "%hours%h, %minutes%m")
                .replace("%days%", Long.toString(seconds / 86400L))
                .replace("%hours%", Long.toString(seconds >= 86400L ? seconds / 3600L % 24 : seconds / 3600L))
                .replace("%minutes%", Long.toString(seconds / 60L % 60));
    }

    private Component format(String text, Map<String, String> values) {
        for (Map.Entry<String, String> value : values.entrySet()) text = text.replace(value.getKey(), value.getValue());
        return C.chat(text).decoration(TextDecoration.ITALIC, false);
    }

    private List<Cell> render(Player player, List<RankedFaction> factions, Map<String, String> values) {
        Cell blank = new Cell(Component.empty(), textures.getOrDefault("blank", new Skin("", "")));
        List<Cell> cells = new ArrayList<>(java.util.Collections.nCopies(80, blank));
        User user = User.get(player);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        for (Row row : rows) {
            List<String> lines = new ArrayList<>();
            if (row.text().contains("%faction-members%")) {
                if (faction != null && faction.hasMember(player.getUniqueId())) {
                    faction.getOnlineMembers().keySet().stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull).filter(player::canSee)
                            .sorted(Comparator.<Player>comparingInt(member -> faction.getMemberType(member.getUniqueId()).getValue()).reversed()
                                    .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(Player::getUniqueId))
                            .forEach(member -> {
                                Faction.MemberType rank = faction.getMemberType(member.getUniqueId());
                                String prefix = Main.getInstance().getTabConfig().getString("ranks." + rank.name().toLowerCase(Locale.ROOT), "");
                                String line = Main.getInstance().getTabConfig().getString("member", "&a%rank%%player%")
                                        .replace("%rank%", prefix).replace("%player%", member.getName());
                                lines.add(row.text().replace("%faction-members%", line));
                            });
                }
            } else if (row.text().contains("%factions%")) {
                for (RankedFaction entry : factions) {
                    String line = Main.getInstance().getTabConfig().getString("faction-entry", "%faction-name% &7[&a%online-members%&7]")
                            .replace("%faction-name%", entry.faction().getFormattedName(user)).replace("%online-members%", Integer.toString(entry.online()));
                    lines.add(row.text().replace("%factions%", line));
                }
            } else lines.add(row.text());
            int end = rows.stream().filter(next -> next.column() == row.column() && next.row() > row.row()).mapToInt(Row::row).min().orElse(20);
            for (int index = 0; index < lines.size() && row.row() + index < end; index++) {
                cells.set(row.column() * 20 + row.row() + index,
                        new Cell(format(lines.get(index), values), textures.getOrDefault(row.skin(), blank.skin())));
            }
        }
        return cells;
    }

    private PlayerInfo entry(int slot, Cell cell) {
        List<TextureProperty> skin = cell.skin().value().isBlank() ? List.of()
                : List.of(new TextureProperty("textures", cell.skin().value(), cell.skin().signature()));
        UserProfile profile = new UserProfile(IDS.get(slot), String.format(Locale.ROOT, "dhcf_tab_%02d", slot), skin);
        return new PlayerInfo(profile, true, 0, GameMode.SURVIVAL, cell.text(), null, 80 - slot, true);
    }

    private void update(Player player, List<RankedFaction> factions, EventScheduler.Upcoming next, Instant now) {
        View view = views.get(player.getUniqueId());
        Map<String, String> values = values(player, next, now);
        List<Cell> cells = render(player, factions, values);
        List<PlayerInfo> added = new ArrayList<>(), changed = new ArrayList<>();
        List<UUID> removed = new ArrayList<>();
        for (int slot = 0; slot < cells.size(); slot++) {
            Cell cell = cells.get(slot);
            if (view.cells.isEmpty() || !cell.skin().equals(view.cells.get(slot).skin())) {
                if (!view.cells.isEmpty()) removed.add(IDS.get(slot));
                added.add(entry(slot, cell));
            } else if (!cell.text().equals(view.cells.get(slot).text())) changed.add(entry(slot, cell));
        }
        if (!removed.isEmpty()) PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerPlayerInfoRemove(removed));
        if (!added.isEmpty()) PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerPlayerInfoUpdate(ADD, added));
        if (!changed.isEmpty()) PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerPlayerInfoUpdate(Action.UPDATE_DISPLAY_NAME, changed));
        view.cells = cells;
        Component header = format(Main.getInstance().getTabConfig().getString("header", "%server-name%"), values);
        Component footer = format(Main.getInstance().getTabConfig().getString("footer", ""), values);
        if (!header.equals(view.header) || !footer.equals(view.footer)) {
            player.sendPlayerListHeaderAndFooter(header, footer);
            view.header = header;
            view.footer = footer;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) { open(e.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        active.remove(e.getPlayer().getUniqueId());
        views.remove(e.getPlayer().getUniqueId());
    }

    public void close() {
        task.cancel();
        active.clear();
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            View view = views.get(player.getUniqueId());
            if (view == null) continue;
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, new WrapperPlayServerPlayerInfoRemove(IDS));
            List<PlayerInfo> restored = Bukkit.getOnlinePlayers().stream().filter(player::canSee).filter(player::isListed).map(other -> {
                PlayerInfo entry = new PlayerInfo(other.getUniqueId());
                entry.setListed(true);
                return entry;
            }).toList();
            if (!restored.isEmpty()) PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                    new WrapperPlayServerPlayerInfoUpdate(Action.UPDATE_LISTED, restored));
            player.sendPlayerListHeaderAndFooter(view.originalHeader == null ? Component.empty() : view.originalHeader,
                    view.originalFooter == null ? Component.empty() : view.originalFooter);
        }
        views.clear();
    }
}
