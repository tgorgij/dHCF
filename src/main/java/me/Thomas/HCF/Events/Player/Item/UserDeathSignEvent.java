package me.Thomas.HCF.Events.Player.Item;

import io.papermc.paper.event.player.PlayerOpenSignEvent;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Items.Items;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UserDeathSignEvent implements Listener {
    private static NamespacedKey itemKey() { return new NamespacedKey(Main.getInstance(), "death-sign-item"); }
    private static NamespacedKey lineKey(int line) { return new NamespacedKey(Main.getInstance(), "death-sign-line-" + line); }

    public static ItemStack create(String player, String killer, DamageCause cause, Instant deathTime) {
        String causeKey = cause == null ? "unknown" : cause.name().toLowerCase(Locale.ROOT).replace('_', '-');
        StringBuilder fallback = new StringBuilder();
        for (String word : causeKey.split("-")) {
            if (!fallback.isEmpty()) fallback.append(' ');
            fallback.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        String causeName = Main.getInstance().getItemsConfig().getString("death-sign.causes." + causeKey, fallback.toString());
        ZoneId timezone;
        try { timezone = ZoneId.of(Main.getInstance().getConfig().getString("settings.timezone", "America/Los_Angeles"), ZoneId.SHORT_IDS); }
        catch (DateTimeException exception) { timezone = ZoneId.of("America/Los_Angeles"); }
        DateTimeFormatter formatter;
        try { formatter = DateTimeFormatter.ofPattern(Main.getInstance().getItemsConfig().getString("death-sign.date-format", "MM/dd/yy HH:mm")); }
        catch (IllegalArgumentException exception) { formatter = DateTimeFormatter.ofPattern("MM/dd/yy HH:mm"); }
        Map<String, String> placeholders = new HashMap<>(Map.of("%player%", player, "%cause%", causeName,
                "%killer%", killer == null ? "" : killer, "%time%", formatter.format(deathTime.atZone(timezone))));
        List<String> format = Main.getInstance().getItemsConfig().getStringList("death-sign.lines." + (killer == null ? "other" : "player"));
        String[] lines = new String[4];
        for (int i = 0; i < lines.length; i++) {
            String line = i < format.size() ? format.get(i) : "";
            for (Map.Entry<String, String> entry : placeholders.entrySet()) line = line.replace(entry.getKey(), entry.getValue());
            lines[i] = line;
        }
        for (int i = 0; i < lines.length; i++) placeholders.put("%line" + (i + 1) + "%", lines[i]);
        ItemStack item = Items.create("death-sign", Material.OAK_SIGN, placeholders);
        ItemMeta meta = item.getItemMeta();
        for (int i = 0; i < lines.length; i++) meta.getPersistentDataContainer().set(lineKey(i), PersistentDataType.STRING, lines[i]);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!(e.getBlockPlaced().getState() instanceof Sign sign)) return;
        ItemStack item = e.getItemInHand();
        if (!item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        String[] lines = new String[4];
        for (int i = 0; i < lines.length; i++) {
            lines[i] = meta.getPersistentDataContainer().get(lineKey(i), PersistentDataType.STRING);
            if (lines[i] == null) return;
        }
        for (Side side : Side.values()) for (int i = 0; i < lines.length; i++) sign.getSide(side).line(i, C.chat(lines[i]));
        ItemStack stored = item.clone();
        stored.setAmount(1);
        sign.getPersistentDataContainer().set(itemKey(), PersistentDataType.BYTE_ARRAY, stored.serializeAsBytes());
        sign.setWaxed(true);
        sign.setAllowedEditorUniqueId(null);
        sign.update(true, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpen(PlayerOpenSignEvent e) {
        if (e.getSign().getPersistentDataContainer().has(itemKey(), PersistentDataType.BYTE_ARRAY)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEdit(SignChangeEvent e) {
        if (e.getBlock().getState() instanceof Sign sign
                && sign.getPersistentDataContainer().has(itemKey(), PersistentDataType.BYTE_ARRAY)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        if (!(e.getBlockState() instanceof Sign sign)) return;
        byte[] saved = sign.getPersistentDataContainer().get(itemKey(), PersistentDataType.BYTE_ARRAY);
        if (saved == null) return;
        ItemStack item = ItemStack.deserializeBytes(saved);
        for (org.bukkit.entity.Item drop : e.getItems()) {
            if (drop.getItemStack().getType() == item.getType()) drop.setItemStack(item.clone());
        }
    }
}
