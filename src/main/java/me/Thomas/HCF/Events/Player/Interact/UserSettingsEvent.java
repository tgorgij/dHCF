package me.Thomas.HCF.Events.Player.Interact;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import java.util.UUID;

public class UserSettingsEvent implements Listener {

    private enum Page { MAIN, SCOREBOARD, MESSAGES }

    private static class Menu implements InventoryHolder {
        private final UUID owner;
        private final Page page;
        private Inventory inventory;

        private Menu(Player p, Page page) {
            this.owner = p.getUniqueId();
            this.page = page;
        }

        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }

    public static void open(Player p) { open(p, Page.MAIN); }

    private static void open(Player p, Page page) {
        Menu menu = new Menu(p, page);
        String title = switch (page) {
            case MAIN -> "title";
            case SCOREBOARD -> "scoreboard-title";
            case MESSAGES -> "messages-title";
        };
        menu.inventory = Bukkit.createInventory(menu, 27, C.chat(text(title)));
        render(menu, User.get(p));
        p.openInventory(menu.inventory);
    }

    private static String text(String key) {
        return Main.getInstance().getGuiConfig().getString("settings." + key);
    }

    private static ItemStack item(Material fallback, String key, Boolean enabled) {
        String path = "settings.items." + key;
        Material configured = Material.matchMaterial(Main.getInstance().getGuiConfig().getString(path + ".material", fallback.name()));
        ItemStack item = new ItemStack(configured == null || configured.isAir() || !configured.isItem() ? fallback : configured);
        ItemMeta meta = item.getItemMeta();
        String status = enabled == null ? "" : Main.getInstance().getLocaleConfig()
                .getString("primary.status." + (enabled ? "enabled" : "disabled"));
        String name = Main.getInstance().getGuiConfig().getString(path + ".name", key).replace("%state%", status);
        meta.displayName(C.chat(name).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : Main.getInstance().getGuiConfig().getStringList(path + ".lore")) {
            lore.add(C.chat(line.replace("%state%", status)).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static void render(Menu menu, User user) {
        switch (menu.page) {
            case MAIN -> {
                menu.inventory.setItem(11, item(Material.PAINTING, "scoreboard-menu", null));
                menu.inventory.setItem(13, item(Material.WRITABLE_BOOK, "messages-menu", null));
                menu.inventory.setItem(15, item(Material.PAPER, "public-chat", user.isPublicChat()));
            }
            case SCOREBOARD -> {
                menu.inventory.setItem(11, item(Material.PAINTING, "scoreboard", user.isScoreboard()));
                menu.inventory.setItem(15, item(Material.GLOWSTONE, "mountains", user.isMountainMessages()));
                menu.inventory.setItem(13, item(Material.CLOCK, "request-report-cooldowns", user.isRequestReportCooldowns()));
            }
            case MESSAGES -> {
                menu.inventory.setItem(11, item(Material.WRITABLE_BOOK, "messages", user.isMessages()));
                menu.inventory.setItem(15, item(Material.NOTE_BLOCK, "message-sounds", user.isMessageSounds()));
            }
        }
        if (menu.page != Page.MAIN) menu.inventory.setItem(22, item(Material.ARROW, "back", null));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || !menu.owner.equals(p.getUniqueId())) return;
        if (Main.getInstance().getStaffMode() != null && Main.getInstance().getStaffMode().isFrozen(p)) return;
        if (e.getClick() != ClickType.LEFT && e.getClick() != ClickType.RIGHT) return;
        int slot = e.getRawSlot();
        Page next = null;
        if (menu.page == Page.MAIN && slot == 11) next = Page.SCOREBOARD;
        else if (menu.page == Page.MAIN && slot == 13) next = Page.MESSAGES;
        else if (menu.page != Page.MAIN && slot == 22) next = Page.MAIN;
        if (next != null) {
            Page page = next;
            // Open another inventory after Bukkit finishes processing the click.
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                if (p.isOnline() && p.getOpenInventory().getTopInventory() == menu.inventory) open(p, page);
            });
            return;
        }
        User user = User.get(p);
        switch (menu.page) {
            case MAIN -> {
                if (slot != 15) return;
                user.setPublicChat(!user.isPublicChat());
            }
            case SCOREBOARD -> {
                if (slot == 11) user.setScoreboard(!user.isScoreboard());
                else if (slot == 15) user.setMountainMessages(!user.isMountainMessages());
                else if (slot == 13) user.setRequestReportCooldowns(!user.isRequestReportCooldowns());
                else return;
            }
            case MESSAGES -> {
                if (slot == 11) user.setMessages(!user.isMessages());
                else if (slot == 15) user.setMessageSounds(!user.isMessageSounds());
                else return;
            }
        }
        user.saveAsync();
        render(menu, user);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }
}
