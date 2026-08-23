package me.Thomas.HCF.Utils.Scoreboard;

import me.Thomas.HCF.Main.Main;
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

    public BoardHandler() {
        Bukkit.getPluginManager().registerEvents(this, Main.getInstance());

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Board board : boards.values()) {
                    if (board.getPlayer().isOnline()) {
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

    public Board getBoard(Player player) {
        return boards.get(player.getUniqueId());
    }
}