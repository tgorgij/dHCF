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

public class BoardHandler implements Listener {

    private final Map<UUID, Board> boards = new HashMap<>();

    private final BukkitTask updateTask;

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
                for (Board board : boards.values()) {
                    if (board.getPlayer().isOnline()) {
                        User.get(board.getPlayer()).expireTimers();
                        board.update();
                    }
                }
            }
        }.runTaskTimer(Main.getInstance(), 0, 2);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Board board = new Board(player);
        boards.put(player.getUniqueId(), board);

        board.update();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        Board board = boards.remove(uuid);

        if (board != null) {
            board.remove();
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