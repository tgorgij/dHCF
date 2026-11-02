package me.Thomas.HCF.Events.Player.Interact;

import io.papermc.paper.event.player.PlayerOpenSignEvent;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class UserShopSignEvent implements Listener {

    private record Trade(boolean buy, Material material, int amount, BigDecimal price) {
        private String serialize() { return (buy ? "[Buy]" : "[Sell]") + ";" + material.name() + ";" + amount + ";" + price.toPlainString(); }
    }
    private record View(UUID player, UUID world, int x, int y, int z) {}
    private final Map<View, BukkitTask> feedback = new HashMap<>();

    private static NamespacedKey key(Side side) {
        return new NamespacedKey(Main.getInstance(), "shop-" + side.name().toLowerCase(Locale.ROOT));
    }

    private static String plain(Component line) {
        return line == null ? "" : PlainTextComponentSerializer.plainText().serialize(line).trim();
    }

    private static boolean marker(String line) { return line.equalsIgnoreCase("[Buy]") || line.equalsIgnoreCase("[Sell]"); }

    private static Trade parse(String[] lines) {
        if (lines.length != 4 || !marker(lines[0])) return null;
        try {
            Material material = Material.matchMaterial(lines[1].replace(' ', '_').toUpperCase(Locale.ROOT));
            int amount = Integer.parseInt(lines[2]);
            BigDecimal price = new BigDecimal(lines[3].startsWith("$") ? lines[3].substring(1) : lines[3]).stripTrailingZeros();
            double value = price.doubleValue();
            if (material == null || material.isAir() || !material.isItem() || amount <= 0
                    || price.signum() <= 0 || price.scale() > 2 || !Double.isFinite(value)
                    || BigDecimal.valueOf(value).compareTo(price) != 0) return null;
            return new Trade(lines[0].equalsIgnoreCase("[Buy]"), material, amount, price);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return null;
        }
    }

    private static Trade read(Sign sign, Side side) {
        String data = sign.getPersistentDataContainer().get(key(side), PersistentDataType.STRING);
        return data == null ? null : parse(data.split(";", -1));
    }

    private static boolean admin(Player p) { return Permission.has(p, "sign.shop.create", "admin"); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent e) {
        if (!(e.getBlock().getState() instanceof Sign sign)) return;
        if (!marker(plain(e.line(0))) && !sign.getPersistentDataContainer().has(key(e.getSide()))) return;
        if (!admin(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return;
        }
        if (!marker(plain(e.line(0)))) return;
        Trade trade = parse(e.lines().stream().map(UserShopSignEvent::plain).toArray(String[]::new));
        if (trade == null) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.shop.invalid")));
            return;
        }
        e.line(0, C.chat(trade.buy() ? "&9[Buy]" : "&9[Sell]"));
        e.line(1, Component.text(trade.material().name().toLowerCase(Locale.ROOT)));
        e.line(2, Component.text(Integer.toString(trade.amount())));
        e.line(3, Component.text(trade.price().toPlainString()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChanged(SignChangeEvent e) {
        if (!admin(e.getPlayer()) || !(e.getBlock().getState() instanceof Sign sign)) return;
        if (!marker(plain(e.line(0))) && !sign.getPersistentDataContainer().has(key(e.getSide()))) return;
        Trade trade = parse(e.lines().stream().map(UserShopSignEvent::plain).toArray(String[]::new));
        if (trade == null) sign.getPersistentDataContainer().remove(key(e.getSide()));
        else sign.getPersistentDataContainer().set(key(e.getSide()), PersistentDataType.STRING, trade.serialize());
        sign.update();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!(e.getBlockPlaced().getState() instanceof Sign sign)) return;
        // A copied sign item must be configured by an administrator after placement.
        for (Side side : Side.values()) sign.getPersistentDataContainer().remove(key(side));
        sign.update();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignOpen(PlayerOpenSignEvent e) {
        if (!admin(e.getPlayer()) && e.getSign().getPersistentDataContainer().has(key(e.getSide()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND
                || e.useInteractedBlock() == Event.Result.DENY || e.getClickedBlock() == null
                || !(e.getClickedBlock().getState() instanceof Sign sign)) return;
        Player p = e.getPlayer();
        Side side = sign.getInteractableSideFor(p);
        Trade trade = read(sign, side);
        if (trade == null || p.isSneaking() && admin(p)) return;
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        boolean success = !p.isDead() && !User.get(p).isInStaffMode() && transact(p, trade);
        showFeedback(p, sign, side, success, trade.buy());
    }

    private static boolean transact(Player p, Trade trade) {
        User user = User.get(p);
        double balance = user.getBalance();
        if (!Double.isFinite(balance) || balance < 0) return false;
        BigDecimal updated = trade.buy() ? BigDecimal.valueOf(balance).subtract(trade.price()) : BigDecimal.valueOf(balance).add(trade.price());
        double newBalance = updated.doubleValue();
        if (updated.signum() < 0 || !Double.isFinite(newBalance) || BigDecimal.valueOf(newBalance).compareTo(updated) != 0) return false;
        ItemStack template = new ItemStack(trade.material());
        ItemStack[] previous = p.getInventory().getStorageContents();
        ItemStack[] contents = new ItemStack[previous.length];
        for (int i = 0; i < contents.length; i++) {
            if (previous[i] != null) {
                previous[i] = previous[i].clone();
                contents[i] = previous[i].clone();
            }
        }
        int remaining = trade.amount();
        int maximum = Math.min(template.getMaxStackSize(), p.getInventory().getMaxStackSize());
        if (trade.buy()) {
            for (ItemStack item : contents) {
                if (remaining == 0) break;
                if (item == null || !item.isSimilar(template)) continue;
                int count = Math.min(remaining, Math.max(0, maximum - item.getAmount()));
                item.setAmount(item.getAmount() + count);
                remaining -= count;
            }
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                if (contents[i] != null && !contents[i].isEmpty()) continue;
                contents[i] = template.clone();
                int count = Math.min(remaining, maximum);
                contents[i].setAmount(count);
                remaining -= count;
            }
        } else {
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack item = contents[i];
                if (item == null || !item.isSimilar(template)) continue;
                int count = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - count);
                if (item.getAmount() == 0) contents[i] = null;
                remaining -= count;
            }
        }
        if (remaining != 0) return false;
        try {
            p.getInventory().setStorageContents(contents);
            user.setBalance(newBalance);
            user.save();
            p.saveData();
            return true;
        } catch (RuntimeException exception) {
            p.getInventory().setStorageContents(previous);
            user.setBalance(balance);
            try {
                user.save();
                p.saveData();
            } catch (RuntimeException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not complete shop sign transaction for " + p.getUniqueId(), exception);
            return false;
        }
    }

    private void showFeedback(Player p, Sign sign, Side side, boolean success, boolean buy) {
        Location location = sign.getLocation();
        View view = new View(p.getUniqueId(), location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        BukkitTask previous = feedback.remove(view);
        if (previous != null) previous.cancel();
        int seconds = Math.max(0, Main.getInstance().getConfig().getInt("shop-signs." + (success ? "success-seconds" : "failure-seconds"), 2));
        if (seconds == 0) {
            restore(view);
            return;
        }
        // Modify this snapshot only; updating the server's sign would show feedback to everyone.
        sign.getSide(side).line(0, C.chat(success ? buy ? "&aPurchased" : "&aSold" : "&cFailed"));
        p.sendBlockUpdate(location, sign);
        feedback.put(view, Bukkit.getScheduler().runTaskLater(Main.getInstance(), () -> {
            feedback.remove(view);
            restore(view);
        }, seconds * 20L));
    }

    private void restore(View view) {
        Player p = Bukkit.getPlayer(view.player());
        World world = Bukkit.getWorld(view.world());
        if (p == null || !p.isOnline() || world == null || !p.getWorld().equals(world) || !world.isChunkLoaded(view.x() >> 4, view.z() >> 4)) return;
        Block block = world.getBlockAt(view.x(), view.y(), view.z());
        if (block.getState() instanceof Sign sign) p.sendBlockUpdate(block.getLocation(), sign);
        else p.sendBlockChange(block.getLocation(), block.getBlockData());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        feedback.entrySet().removeIf(entry -> {
            if (!entry.getKey().player().equals(e.getPlayer().getUniqueId())) return false;
            entry.getValue().cancel();
            return true;
        });
    }

    @EventHandler
    public void onDisable(PluginDisableEvent e) {
        if (e.getPlugin() != Main.getInstance()) return;
        feedback.forEach((view, task) -> {
            task.cancel();
            restore(view);
        });
        feedback.clear();
    }
}
