package me.Thomas.HCF.Utils.Scoreboard;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Board {
    private static final int MAX_LINES = 15;
    private static final Pattern TIMER_DISPLAY = Pattern.compile("<display=%has-player-([a-zA-Z]+)-timer%>");
    private record Line(String text, Timer.Type timer, String placeholder) {}

    private final Player player;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final List<Line> configuredLines = new ArrayList<>();
    private final List<String> previousLines = new ArrayList<>();
    private boolean visible;

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
        for (String text : lines) {
            Matcher matcher = TIMER_DISPLAY.matcher(text);
            if (matcher.find()) {
                String timerName = matcher.group(1).toLowerCase(Locale.ROOT);
                try {
                    configuredLines.add(new Line(text.replace(matcher.group(), ""),
                            Timer.Type.valueOf(timerName.toUpperCase(Locale.ROOT)),
                            "%player-" + timerName + "-timer%"));
                } catch (IllegalArgumentException ignored) {
                    Main.getInstance().getLogger().warning("Unknown scoreboard timer: " + timerName);
                }
            } else {
                configuredLines.add(new Line(text, null, null));
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
        for (Line configured : configuredLines) {
            String text = configured.text();
            if (configured.timer() != null) {
                Timer timer = user.getTimer(configured.timer());
                if (timer == null || timer.getRemainingTimeMillis() <= 0) continue;
                text = text.replace(configured.placeholder(), C.formatTime(timer.getRemainingTimeMillis()));
            }
            lines.add(text);
            if (lines.size() == MAX_LINES) break;
        }
        boolean show = lines.size() > 2;
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

    public void remove() {
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
