package me.Thomas.HCF.Utils.Scoreboard;

import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.IO.User.User;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

public class BoardHandler implements Listener {

    private final Map<UUID, Board> boards = new HashMap<>();

    private final BukkitTask updateTask;
    private int focusTicks;
    private Set<String> archerTaggedNames = Set.of();

    public BoardHandler() {
        Bukkit.getPluginManager().registerEvents(this, Main.getInstance());

        for (Player player : Bukkit.getOnlinePlayers()) {
            Main.getInstance().addUser(player);
            User.get(player).unpauseTimers();
            boards.put(player.getUniqueId(), new Board(player));
        }
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                Set<String> tags = Board.getArcherTaggedNames();
                boolean refreshNames = focusTicks++ % 10 == 0 || !tags.equals(archerTaggedNames);
                archerTaggedNames = tags;
                for (Board board : boards.values()) {
                    if (board.getPlayer().isOnline()) {
                        User.get(board.getPlayer()).expireTimers();
                        board.update();
                        if (refreshNames) board.updateFocus(tags);
                    }
                }
            }
        }.runTaskTimer(Main.getInstance(), 0, 2);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();

        Board board = new Board(player);
        boards.put(player.getUniqueId(), board);

        board.update();
        refreshFocus();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();

        Board board = boards.remove(uuid);

        if (board != null) {
            board.remove();
        }
    }

    public void refreshFocus() {
        Set<String> tags = Board.getArcherTaggedNames();
        archerTaggedNames = tags;
        for (Board board : boards.values()) {
            if (board.getPlayer().isOnline()) board.updateFocus(tags);
        }
    }

    public void close() {
        updateTask.cancel();
        for (Board board : boards.values()) board.remove();
        boards.clear();
    }

    public Board getBoard(Player player) {
        return boards.get(player.getUniqueId());
    }
}
