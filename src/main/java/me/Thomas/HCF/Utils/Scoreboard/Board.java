package me.Thomas.HCF.Utils.Scoreboard;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.KOTH.KOTH;
import me.Thomas.HCF.IO.Conquest.Conquest;
import me.Thomas.HCF.IO.DTC.DTC;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Ability.AbilityItems;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import me.Thomas.HCF.Utils.Timer.ServerTimer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Board {
    private static final int MAX_LINES = 15;
    private static final Pattern TIMER_DISPLAY = Pattern.compile("<display=%has-(player|server)-([a-zA-Z]+)-timer%>");
    private static final String RALLY_DISPLAY = "<display=%has-faction-rally%>";
    private static final String LINES_BELOW_DISPLAY = "<display=%has-lines-below%>";
    private static final Pattern DIVIDER = Pattern.compile("[-_=─━═\\s]*");
    private static final String CONQUEST_DISPLAY = "<display=%has-server-conquest%>";
    private static final String KOTH_DISPLAY = "<display=%has-server-koths%>";
    private record Line(String text, Timer.Type timer, ServerTimer.Type serverTimer, String placeholder,
                        boolean rallyOnly, boolean kothsOnly, boolean conquestOnly) {}

    private final Player player;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final List<Line> configuredLines = new ArrayList<>();
    private final List<String> rallyLines;
    private final List<String> staffLines;
    private final String kothLine;
    private final List<String> conquestLines;
    private final String conquestLeader;
    private final List<String> previousLines = new ArrayList<>();
    private boolean visible;
    private Team focusTeam;
    private Team archerTagTeam;

    public Board(Player player) {
        this(player, Bukkit.getScoreboardManager().getNewScoreboard(),
                Main.getInstance().getScoreboardConfig().getString("title", "dHCF"),
                Main.getInstance().getScoreboardConfig().getStringList("boards.primary"));
    }

    private Board(Player player, Scoreboard scoreboard, String title, List<String> lines) {
        this(player, scoreboard, scoreboard.registerNewObjective("dhcf", Criteria.DUMMY, C.chat(title)), lines);
    }

    Board(Player player, Scoreboard scoreboard, Objective objective, List<String> lines) {
        this.player = player;
        this.scoreboard = scoreboard;
        this.objective = objective;
        this.conquestLines = Main.getInstance().getScoreboardConfig().getStringList("boards.conquest");
        this.conquestLeader = Main.getInstance().getScoreboardConfig().getString("boards.conquest-leader", "&2%rank%. %faction%&7: &f%points%");
        this.staffLines = Main.getInstance().getScoreboardConfig().getStringList("boards.staff");
        this.rallyLines = Main.getInstance().getScoreboardConfig().getStringList("boards.rally");
        this.kothLine = Main.getInstance().getScoreboardConfig().getString("boards.koth", "%name% KOTH&7: &c%time%");
        for (String configuredText : lines) {
            boolean rallyOnly = configuredText.contains(RALLY_DISPLAY);
            boolean kothsOnly = configuredText.contains(KOTH_DISPLAY);
            boolean conquestOnly = configuredText.contains(CONQUEST_DISPLAY);
            String text = configuredText.replace(RALLY_DISPLAY, "").replace(KOTH_DISPLAY, "").replace(CONQUEST_DISPLAY, "").stripTrailing();
            Matcher matcher = TIMER_DISPLAY.matcher(text);
            if (matcher.find()) {
                String scope = matcher.group(1);
                String timerName = matcher.group(2).toLowerCase(Locale.ROOT);
                try {
                    configuredLines.add(new Line(text.replace(matcher.group(), ""),
                            scope.equals("player") ? Timer.Type.valueOf(timerName.toUpperCase(Locale.ROOT)) : null,
                            scope.equals("server") ? ServerTimer.Type.valueOf(timerName.toUpperCase(Locale.ROOT)) : null,
                            "%" + scope + "-" + timerName + "-timer%", rallyOnly, kothsOnly, conquestOnly));
                } catch (IllegalArgumentException ignored) {
                    Main.getInstance().getLogger().warning("Unknown scoreboard timer: " + timerName);
                }
            } else {
                configuredLines.add(new Line(text, null, null, null, rallyOnly, kothsOnly, conquestOnly));
            }
        }
        player.setScoreboard(scoreboard);
    }

    public void update() {
        if (!player.isOnline()) return;
        update(User.get(player));
    }

    void update(User user) {
        List<String> lines = new ArrayList<>(MAX_LINES);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        Location rally = faction == null || !faction.hasMember(player.getUniqueId()) ? null : faction.getRally();
        Conquest conquest = Conquest.get();
        List<KOTH> koths = Main.getInstance().getAllKOTHs().values().stream().filter(KOTH::isRunning)
                .sorted(java.util.Comparator.comparing(KOTH::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        for (Line configured : configuredLines) {
            if (configured.rallyOnly() && rally == null) continue;
            if (configured.kothsOnly() && koths.isEmpty()) continue;
            if (configured.conquestOnly() && (conquest == null || !conquest.isRunning())) continue;
            String text = configured.text();
            if (text.contains("<display=%has-bard%>")) {
                if (!user.isBardActive()) continue;
                text = text.replace("<display=%has-bard%>", "").stripTrailing();
            }
            if (text.contains("%dtc%")) {
                if (DTC.get() == null || !DTC.get().isRunning()) continue;
                text = text.replace("%dtc%", Main.getInstance().getScoreboardConfig().getString("boards.dtc", "&cDTC&7: &f%health%/%max-health%")
                        .replace("%health%", Integer.toString(DTC.get().getHealth()))
                        .replace("%max-health%", Integer.toString(DTC.get().getMaxHealth())));
            }
            if (text.contains("%bard-energy%")) {
                text = text.replace("%bard-energy%", java.math.BigDecimal.valueOf(user.getBardEnergy())
                        .setScale(1, java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString());
            }
            if (text.contains("%mod-mode%")) {
                if (!user.isInStaffMode()) continue;
                long online = Bukkit.getOnlinePlayers().stream().filter(player::canSee).count();
                String gamemode = player.getGameMode().name().toLowerCase(Locale.ROOT);
                gamemode = Character.toUpperCase(gamemode.charAt(0)) + gamemode.substring(1);
                for (String staffLine : staffLines) {
                    lines.add(text.replace("%mod-mode%", staffLine
                            .replace("%gamemode%", gamemode)
                            .replace("%vanished%", Main.getInstance().getStaffMode().status(user.isVanished()))
                            .replace("%online%", String.valueOf(online))
                            .replace("%total%", String.valueOf(Bukkit.getMaxPlayers()))));
                    if (lines.size() == MAX_LINES) break;
                }
                if (lines.size() == MAX_LINES) break;
                continue;
            }
            if (configured.timer() != null) {
                if (!user.isRequestReportCooldowns() && (configured.timer() == Timer.Type.REQUEST || configured.timer() == Timer.Type.REPORT)) continue;
                Timer timer = user.getTimer(configured.timer());
                if (timer == null || timer.getRemainingTimeMillis() <= 0) continue;
                text = text.replace(configured.placeholder(), C.formatTime(timer.getRemainingTimeMillis()));
            }
            if (configured.serverTimer() != null) {
                ServerTimer timer = Main.getInstance().getServerTimer(configured.serverTimer());
                if (timer == null || timer.getRemainingTimeMillis() <= 0) continue;
                text = text.replace(configured.placeholder(), C.formatTime(timer.getRemainingTimeMillis()));
            }
            if (text.contains("%ability-cooldowns%")) {
                if (!user.isAbilityCooldowns()) continue;
                String format = Main.getInstance().getScoreboardConfig().getString("boards.ability-timer", "%name%&7: &c%time%");
                for (ServerTimer timer : AbilityItems.timers(player).values()) {
                    lines.add(text.replace("%ability-cooldowns%", format.replace("%name%", timer.getName())
                            .replace("%time%", C.formatTime(timer.getRemainingTimeMillis()))));
                    if (lines.size() == MAX_LINES) break;
                }
            } else if (text.contains("%custom-server-timers%") || text.contains("%custom-user-timers%")) {
                boolean server = text.contains("%custom-server-timers%");
                String placeholder = server ? "%custom-server-timers%" : "%custom-user-timers%";
                String format = Main.getInstance().getScoreboardConfig().getString("boards.custom-timer", "%name%&7: &c%time%");
                for (ServerTimer timer : (server ? Main.getInstance().getCustomServerTimers() : user.getCustomTimers()).values()) {
                    lines.add(text.replace(placeholder, format.replace("%name%", timer.getName())
                            .replace("%time%", C.formatTime(timer.getRemainingTimeMillis()))));
                    if (lines.size() == MAX_LINES) break;
                }
            } else if (text.contains("%conquest%")) {
                if (conquest == null || !conquest.isRunning()) continue;
                for (String line : conquestLines(conquest, user)) {
                    lines.add(text.replace("%conquest%", line));
                    if (lines.size() == MAX_LINES) break;
                }
            } else if (text.contains("%koths%")) {
                for (KOTH koth : koths) {
                    lines.add(text.replace("%koths%", kothLine
                            .replace("%name%", C.getColorCode(koth.getColor()) + koth.getName())
                            .replace("%time%", koth.getFormattedTime())));
                    if (lines.size() == MAX_LINES) break;
                }
            } else if (text.contains("%rally%")) {
                if (rally == null || rally.getWorld() == null) continue;
                String dimension = C.getFormattedDimension(rally.getWorld());
                String location = C.getFormattedLocation(rally);
                for (String rallyLine : rallyLines) {
                    lines.add(text.replace("%rally%", rallyLine
                            .replace("%dimension%", dimension).replace("%location%", location)));
                    if (lines.size() == MAX_LINES) break;
                }
            } else {
                lines.add(text);
            }
            if (lines.size() == MAX_LINES) break;
        }
        boolean contentBelow = false;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String text = lines.get(i);
            if (text.contains(LINES_BELOW_DISPLAY)) {
                if (!contentBelow) {
                    lines.remove(i);
                    continue;
                }
                text = text.replace(LINES_BELOW_DISPLAY, "").stripTrailing();
                lines.set(i, text);
            }
            String plain = PlainTextComponentSerializer.plainText().serialize(C.chat(text));
            if (!DIVIDER.matcher(plain).matches()) contentBelow = true;
        }
        boolean show = user.isScoreboard() && lines.size() > 2;
        if (visible != show) {
            objective.setDisplaySlot(show ? DisplaySlot.SIDEBAR : null);
            visible = show;
        }
        if (!show) lines.clear();
        for (int i = 0; i < lines.size(); i++) {
            if (i < previousLines.size() && previousLines.get(i).equals(lines.get(i))) continue;
            Score score = objective.getScore("dhcf_line_" + i);
            if (i >= previousLines.size()) score.setScore(MAX_LINES - i);
            score.customName(C.chat(lines.get(i)));
        }
        for (int i = lines.size(); i < previousLines.size(); i++) {
            scoreboard.resetScores("dhcf_line_" + i);
        }
        previousLines.clear();
        previousLines.addAll(lines);
    }

    private List<String> conquestLines(Conquest conquest, User user) {
        List<String> result = new ArrayList<>();
        List<Conquest.Standing> leaders = conquest.getLeaders();
        for (String configured : conquestLines) {
            String line = configured.replace("%name%", conquest.getName());
            for (Conquest.Color color : Conquest.Color.values()) {
                line = line.replace("%" + color.name().toLowerCase(Locale.ROOT) + "-time%", conquest.getZone(color).getFormattedTime());
            }
            if (line.contains("%leaders%")) {
                for (int i = 0; i < leaders.size(); i++) {
                    Conquest.Standing standing = leaders.get(i);
                    Faction team = Faction.get(standing.faction());
                    if (team == null) continue;
                    result.add(line.replace("%leaders%", conquestLeader.replace("%rank%", Integer.toString(i + 1))
                            .replace("%faction%", team.getFormattedName(user)).replace("%points%", Integer.toString(standing.points()))));
                }
            } else result.add(line);
        }
        return result;
    }

    static Set<String> getArcherTaggedNames() {
        Set<String> names = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (User.get(p).hasTimer(Timer.Type.ARCHERTAG)) names.add(p.getName());
        }
        return names;
    }

    void updateFocus() {
        updateFocus(getArcherTaggedNames());
    }

    void updateFocus(Set<String> taggedNames) {
        UUID own = User.get(player).getFaction();
        Main.Focus focus = own == null ? null : Main.getInstance().getFocus(own);
        Set<String> names = new HashSet<>();
        if (focus != null) {
            Set<UUID> targets = Set.of(focus.target());
            if (focus.faction()) {
                Faction targetFaction = Faction.get(focus.target());
                targets = targetFaction == null ? Set.of() : targetFaction.getMembers().keySet();
            }
            for (UUID uuid : targets) {
                Player target = Bukkit.getPlayer(uuid);
                if (target != null && !own.equals(User.get(target).getFaction())) names.add(target.getName());
            }
        }
        names.removeAll(taggedNames);
        if (focusTeam == null && !names.isEmpty()) {
            focusTeam = createNameTeam("dhcf_focus", "factions.colors.focus", "PINK", NamedTextColor.LIGHT_PURPLE);
        }
        if (archerTagTeam == null && !taggedNames.isEmpty()) {
            archerTagTeam = createNameTeam("dhcf_archer", "factions.colors.archer-tag", "YELLOW", NamedTextColor.YELLOW);
        }
        updateNameTeam(focusTeam, names);
        updateNameTeam(archerTagTeam, taggedNames);
    }

    private Team createNameTeam(String name, String path, String defaultColor, NamedTextColor fallback) {
        Team team = scoreboard.registerNewTeam(name);
        String code = C.getColorCode(Main.getInstance().getConfig().getString(path, defaultColor));
        TextColor color = C.chat(code + "x").color();
        team.color(color == null ? fallback : NamedTextColor.nearestTo(color));
        team.setCanSeeFriendlyInvisibles(false);
        return team;
    }

    private void updateNameTeam(Team team, Set<String> names) {
        if (team == null) return;
        for (String entry : team.getEntries()) {
            if (!names.contains(entry)) team.removeEntry(entry);
        }
        for (String name : names) {
            if (!team.hasEntry(name)) team.addEntry(name);
        }
    }

    public void remove() {
        if (focusTeam != null) focusTeam.unregister();
        if (archerTagTeam != null) archerTagTeam.unregister();
        for (int i = 0; i < previousLines.size(); i++) scoreboard.resetScores("dhcf_line_" + i);
        previousLines.clear();
        if (player.isOnline() && player.getScoreboard() == scoreboard) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    public Player getPlayer() { return player; }
    public Scoreboard getScoreboard() { return scoreboard; }
    public Objective getObjective() { return objective; }
}
