package me.Thomas.HCF.Utils.Staff;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.Comparator;
import net.kyori.adventure.text.Component;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class StaffMode implements Listener {

    private record Session(boolean vanished) {}
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Set<UUID> bypass = new HashSet<>();
    private final Set<UUID> vanished = new HashSet<>();
    private final Map<UUID, Integer> vanishClicks = new HashMap<>();
    private final Set<UUID> frozen = new HashSet<>();
    private boolean freezeAll;
    private final NamespacedKey inventoryKey = new NamespacedKey(Main.getInstance(), "staff-inventory");
    private final NamespacedKey cursorKey = new NamespacedKey(Main.getInstance(), "staff-cursor");
    private final NamespacedKey slotKey = new NamespacedKey(Main.getInstance(), "staff-slot");
    private final NamespacedKey flightKey = new NamespacedKey(Main.getInstance(), "staff-flight");
    private final NamespacedKey toolKey = new NamespacedKey(Main.getInstance(), "staff-tool");

    private static class Menu implements InventoryHolder {
        private Inventory inventory;
        private final UUID target;
        private final int page;
        private final Map<Integer, UUID> players = new HashMap<>();
        private Menu(UUID target, int page) { this.target = target; this.page = page; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    public StaffMode() {
        for (Player p : Bukkit.getOnlinePlayers()) restore(p);
        Bukkit.getScheduler().runTaskTimer(Main.getInstance(), this::refreshInventories, 1L, 5L);
    }

    public boolean isEnabled(Player p) { return sessions.containsKey(p.getUniqueId()); }
    public boolean isVanished(Player p) { return vanished.contains(p.getUniqueId()); }
    public boolean isStaff(Player p) {
        return Permission.has(p, "command.staff", "staff") || Permission.has(p, "command.vanish", "staff")
                || Permission.has(p, "command.freeze", "staff") || Permission.has(p, "command.invsee", "staff");
    }
    public boolean isStaffOrAdmin(Player p) { return isStaff(p) || Permission.has(p, "admin"); }

    private void announceConnection(Player p, boolean joined) {
        if (!isStaffOrAdmin(p)) return;
        Component notification = C.chat(Main.getInstance().getLocaleConfig()
                .getString("events.staff." + (joined ? "joined" : "left")).replace("%player%", p.getName()));
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (isStaffOrAdmin(staff) && (joined || !staff.getUniqueId().equals(p.getUniqueId()))) staff.sendMessage(notification);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStaffJoin(PlayerJoinEvent e) {
        announceConnection(e.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStaffQuit(PlayerQuitEvent e) {
        announceConnection(e.getPlayer(), false);
    }

    public boolean isFrozen(Player p) {
        return (freezeAll || frozen.contains(p.getUniqueId())) && !canBypassFreeze(p);
    }
    private boolean canBypassFreeze(Player p) { return isStaff(p) || Permission.has(p, "freeze.bypass"); }

    public boolean isRestricted(Player p) { return isFrozen(p) || isEnabled(p) && !bypass.contains(p.getUniqueId()); }

    public void message(CommandSender sender, String key, String... replacements) {
        String text = Main.getInstance().getLocaleConfig().getString("commands." + key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) text = text.replace(replacements[i], replacements[i + 1]);
        sender.sendMessage(C.chat(text));
    }

    public void toggle(Player p) {
        if (isEnabled(p)) {
            Session session = sessions.get(p.getUniqueId());
            p.closeInventory();
            restore(p);
            sessions.remove(p.getUniqueId());
            bypass.remove(p.getUniqueId());
            setVanished(p, session.vanished());
            message(p, "staff.disabled");
            return;
        }
        if (p.isDead() || isFrozen(p)) {
            message(p, "staff.unavailable");
            return;
        }
        closeInspections(p);
        ItemStack cursor = p.getItemOnCursor().clone();
        p.setItemOnCursor(null);
        p.closeInventory();
        p.getPersistentDataContainer().set(inventoryKey, PersistentDataType.BYTE_ARRAY,
                ItemStack.serializeItemsAsBytes(p.getInventory().getContents()));
        p.getPersistentDataContainer().set(cursorKey, PersistentDataType.BYTE_ARRAY,
                ItemStack.serializeItemsAsBytes(new ItemStack[]{cursor}));
        p.getPersistentDataContainer().set(slotKey, PersistentDataType.INTEGER, p.getInventory().getHeldItemSlot());
        p.getPersistentDataContainer().set(flightKey, PersistentDataType.STRING,
                p.getGameMode().name() + ";" + p.getAllowFlight() + ";" + p.isFlying());
        p.saveData();
        sessions.put(p.getUniqueId(), new Session(isVanished(p)));
        p.leaveVehicle();
        p.setAllowFlight(true);
        p.setFlying(true);
        User.get(p).removeTimer(Timer.Type.HOME);
        User.get(p).removeTimer(Timer.Type.STUCK);
        User.get(p).removeTimer(Timer.Type.LOGOUT);
        p.getInventory().clear();
        p.getInventory().setHeldItemSlot(0);
        tools(p);
        setVanished(p, true);
        message(p, "staff.enabled");
    }

    private void restore(Player p) {
        byte[] saved = p.getPersistentDataContainer().get(inventoryKey, PersistentDataType.BYTE_ARRAY);
        if (saved == null) return;
        ItemStack[] contents = ItemStack.deserializeItemsFromBytes(saved);
        byte[] savedCursor = p.getPersistentDataContainer().get(cursorKey, PersistentDataType.BYTE_ARRAY);
        ItemStack[] cursor = savedCursor == null ? new ItemStack[0] : ItemStack.deserializeItemsFromBytes(savedCursor);
        int slot = p.getPersistentDataContainer().getOrDefault(slotKey, PersistentDataType.INTEGER, 0);
        if (contents.length != p.getInventory().getSize() || cursor.length > 1) {
            throw new IllegalStateException("Invalid staff inventory backup for " + p.getUniqueId());
        }
        p.getInventory().setContents(contents);
        p.setItemOnCursor(cursor.length == 0 ? null : cursor[0]);
        p.getInventory().setHeldItemSlot(Math.clamp(slot, 0, 8));
        String flight = p.getPersistentDataContainer().get(flightKey, PersistentDataType.STRING);
        if (flight != null) {
            String[] values = flight.split(";", -1);
            boolean inherentFlight = p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR;
            boolean allowed = inherentFlight || values.length == 3 && values[0].equals(p.getGameMode().name())
                    && Boolean.parseBoolean(values[1]);
            if (!allowed) p.setFlying(false);
            p.setAllowFlight(allowed);
            if (allowed) p.setFlying(p.getGameMode() == GameMode.SPECTATOR || values.length == 3 && Boolean.parseBoolean(values[2]));
            p.setFallDistance(0);
        }
        p.getPersistentDataContainer().remove(flightKey);
        p.getPersistentDataContainer().remove(inventoryKey);
        p.getPersistentDataContainer().remove(cursorKey);
        p.getPersistentDataContainer().remove(slotKey);
        p.saveData();
    }

    public void toggleBypass(Player p) {
        if (!isEnabled(p)) { message(p, "staff.not-enabled"); return; }
        boolean enabled = bypass.add(p.getUniqueId());
        if (!enabled) bypass.remove(p.getUniqueId());
        message(p, "staff.bypass", "%state%", status(enabled));
    }

    public String status(boolean enabled) {
        return Main.getInstance().getLocaleConfig().getString("primary.status." + (enabled ? "enabled" : "disabled"));
    }

    public void setVanished(Player p, boolean enabled) {
        if (enabled) vanished.add(p.getUniqueId());
        else vanished.remove(p.getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) visibility(viewer, p);
        if (isEnabled(p)) {
            for (int slot = 0; slot < 36; slot++) {
                ItemStack item = p.getInventory().getItem(slot);
                if (!isTool(item)) continue;
                String name = item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
                if ("show".equals(name) || "hide".equals(name)) p.getInventory().setItem(slot, null);
            }
            placeTool(p, enabled ? "show" : "hide", enabled ? Material.LIME_DYE : Material.GRAY_DYE, 8);
        }
    }

    private void visibility(Player viewer, Player target) {
        if (isVanished(target) && !isStaff(viewer) && !Permission.has(viewer, "vanish.see")) viewer.hidePlayer(Main.getInstance(), target);
        else viewer.showPlayer(Main.getInstance(), target);
    }

    public void toggleFreeze(CommandSender sender, Player target) {
        if (canBypassFreeze(target)) { message(sender, "freeze.bypass"); return; }
        if (freezeAll) { message(sender, "freeze.all-active"); return; }
        boolean enabled = frozen.add(target.getUniqueId());
        if (!enabled) frozen.remove(target.getUniqueId());
        if (enabled) stop(target);
        message(target, enabled ? "freeze.frozen" : "freeze.unfrozen");
        message(sender, "freeze.success", "%player%", target.getName(), "%state%", enabled ? "frozen" : "unfrozen");
    }

    public void toggleFreezeAll(CommandSender sender) {
        freezeAll = !freezeAll;
        frozen.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (canBypassFreeze(p)) continue;
            if (freezeAll) stop(p);
            message(p, freezeAll ? "freeze.frozen" : "freeze.unfrozen");
        }
        message(sender, "freeze.all", "%state%", freezeAll ? "frozen" : "unfrozen");
    }

    private void stop(Player p) {
        p.closeInventory();
        p.leaveVehicle();
        p.setGliding(false);
        p.setVelocity(new org.bukkit.util.Vector());
        User.get(p).removeTimer(Timer.Type.HOME);
        User.get(p).removeTimer(Timer.Type.STUCK);
        User.get(p).removeTimer(Timer.Type.LOGOUT);
    }

    private ItemStack tool(String name, Material fallback) {
        return tool(name, fallback, "");
    }

    private ItemStack tool(String name, Material fallback, String player) {
        boolean gui = name.equals("player") || name.equals("previous") || name.equals("next");
        FileConfiguration config = gui ? Main.getInstance().getGuiConfig() : Main.getInstance().getItemsConfig();
        String path = (gui ? "staff.online.items." : "staff.") + name;
        Material configured = Material.matchMaterial(config.getString(path + ".material", fallback.name()));
        ItemStack item = new ItemStack(configured == null || configured.isAir() || !configured.isItem() ? fallback : configured);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(C.chat(config.getString(path + ".name", name)
                .replace("%player%", player)).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : config.getStringList(path + ".lore")) {
            lore.add(C.chat(line.replace("%player%", player)).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, name);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isTool(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(toolKey);
    }

    private void placeTool(Player p, String name, Material material, int fallbackSlot) {
        int slot = Math.clamp(Main.getInstance().getItemsConfig().getInt("staff." + name + ".slot", fallbackSlot), 0, 35);
        ItemStack current = p.getInventory().getItem(slot);
        if (current != null && !current.isEmpty()) slot = p.getInventory().firstEmpty();
        if (slot >= 0 && slot < 36) p.getInventory().setItem(slot, tool(name, material));
    }

    private void tools(Player p) {
        placeTool(p, "compass", Material.COMPASS, 0);
        placeTool(p, "random", Material.CLOCK, 1);
        placeTool(p, "inspect", Material.BOOK, 2);
        placeTool(p, "freeze", Material.BLUE_ICE, 3);
        placeTool(p, "staff", Material.PLAYER_HEAD, 4);
        if (p.hasPermission("worldedit.wand")) placeTool(p, "wand", Material.WOODEN_AXE, 5);
    }

    public void inspect(Player viewer, Player target) {
        if (isEnabled(target)) { message(viewer, "invsee.target-in-mode"); return; }
        Menu menu = new Menu(target.getUniqueId(), 0);
        int rows = Math.clamp(Main.getInstance().getGuiConfig().getInt("staff.inventory.rows", 6), 5, 6);
        menu.inventory = Bukkit.createInventory(menu, rows * 9, C.chat(Main.getInstance().getGuiConfig()
                .getString("staff.inventory.title", "&eInventory: %player%").replace("%player%", target.getName())));
        copyInventory(menu, target);
        viewer.openInventory(menu.inventory);
    }

    private void copyInventory(Menu menu, Player target) {
        for (int slot = 0; slot < target.getInventory().getSize(); slot++) {
            ItemStack item = target.getInventory().getItem(slot);
            menu.inventory.setItem(slot, item == null ? null : item.clone());
        }
    }

    private void refreshInventories() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (isEnabled(p) && !p.getAllowFlight()) p.setAllowFlight(true);
            if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu) || menu.target == null) continue;
            Player target = Bukkit.getPlayer(menu.target);
            if (target == null || !target.isOnline() || target.isDead() || isEnabled(target) || !Permission.has(p, "command.invsee", "staff")) p.closeInventory();
            else copyInventory(menu, target);
        }
    }

    private void staffMenu(Player p, int requestedPage) {
        List<? extends Player> staff = Bukkit.getOnlinePlayers().stream().filter(this::isStaff)
                .filter(p::canSee).sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER)).toList();
        int rows = Math.clamp(Main.getInstance().getGuiConfig().getInt("staff.online.rows", 6), 2, 6);
        int capacity = (rows - 1) * 9;
        int page = Math.clamp(requestedPage, 0, Math.max(0, (staff.size() - 1) / capacity));
        Menu menu = new Menu(null, page);
        menu.inventory = Bukkit.createInventory(menu, rows * 9, C.chat(Main.getInstance().getGuiConfig()
                .getString("staff.online.title", "&eOnline Staff")));
        for (int i = page * capacity; i < Math.min(staff.size(), (page + 1) * capacity); i++) {
            Player target = staff.get(i);
            ItemStack head = tool("player", Material.PLAYER_HEAD, target.getName());
            if (head.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(target);
                head.setItemMeta(meta);
            }
            menu.inventory.setItem(i % capacity, head);
            menu.players.put(i % capacity, target.getUniqueId());
        }
        if (page > 0) menu.inventory.setItem(capacity, tool("previous", Material.ARROW));
        if ((page + 1) * capacity < staff.size()) menu.inventory.setItem(menu.inventory.getSize() - 1, tool("next", Material.ARROW));
        p.openInventory(menu.inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (isFrozen(p)) { e.setCancelled(true); return; }
        if (!isEnabled(p)) return;
        String name = isTool(e.getItem()) ? e.getItem().getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING) : null;
        if (name == null) { if (isRestricted(p)) e.setCancelled(true); return; }
        if ("compass".equals(name) || "wand".equals(name)) {
            e.setUseItemInHand(Event.Result.DENY);
            e.setUseInteractedBlock(Event.Result.DENY);
            return;
        }
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND || !e.getAction().isRightClick()) return;
        switch (name) {
            case "random" -> {
                List<? extends Player> players = Bukkit.getOnlinePlayers().stream().filter(target -> !target.equals(p))
                        .filter(p::canSee).filter(target -> !isEnabled(target)).toList();
                if (players.isEmpty()) message(p, "staff.no-targets");
                else p.teleport(players.get(ThreadLocalRandom.current().nextInt(players.size())).getLocation());
            }
            case "staff" -> staffMenu(p, 0);
            case "show", "hide" -> {
                // A block interaction can also send an air interaction in the same tick.
                Integer previous = vanishClicks.put(p.getUniqueId(), p.getTicksLived());
                if (previous == null || previous != p.getTicksLived()) setVanished(p, !isVanished(p));
            }
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        if (isRestricted(p) || isEnabled(p) && isTool(held)) e.setCancelled(true);
        if (!isEnabled(p) || isFrozen(p) || e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Player target)) return;
        ItemStack item = p.getInventory().getItemInMainHand();
        if (!isTool(item)) return;
        String name = item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        if ("inspect".equals(name) && Permission.has(p, "command.invsee", "staff")) inspect(p, target);
        if ("freeze".equals(name) && Permission.has(p, "command.freeze", "staff")) toggleFreeze(p, target);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityInteractAt(PlayerInteractAtEntityEvent e) {
        Player p = e.getPlayer();
        if (isRestricted(p) || isEnabled(p) && isTool(p.getInventory().getItemInMainHand())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (isFrozen(p)) { e.setCancelled(true); return; }
        if (e.getView().getTopInventory().getHolder() instanceof Menu menu) {
            e.setCancelled(true);
            if (menu.target == null) {
                if (!isEnabled(p) || !Permission.has(p, "command.staff", "staff")) return;
                int slot = e.getRawSlot();
                Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
                    if (!p.isOnline() || !isEnabled(p) || p.getOpenInventory().getTopInventory() != menu.inventory) return;
                    if (slot == menu.inventory.getSize() - 9 && menu.page > 0) staffMenu(p, menu.page - 1);
                    else if (slot == menu.inventory.getSize() - 1 && menu.inventory.getItem(menu.inventory.getSize() - 1) != null) staffMenu(p, menu.page + 1);
                    else {
                        Player target = menu.players.containsKey(slot) ? Bukkit.getPlayer(menu.players.get(slot)) : null;
                        if (target != null && target.isOnline() && isStaff(target) && p.canSee(target)) {
                            p.closeInventory();
                            p.teleport(target.getLocation());
                        }
                    }
                });
                return;
            }
            if (!Permission.has(p, "command.invsee", "staff") || !Permission.has(p, "command.invsee.modify")) return;
            Player target = Bukkit.getPlayer(menu.target);
            if (target == null || target.isDead() || isEnabled(target) || isFrozen(target)) return;
            if (e.getClickedInventory() == p.getInventory() && !isEnabled(p)) {
                switch (e.getAction()) {
                    case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, PLACE_ALL, PLACE_SOME, PLACE_ONE,
                         SWAP_WITH_CURSOR, DROP_ALL_SLOT, DROP_ONE_SLOT, NOTHING -> e.setCancelled(false);
                    default -> { }
                }
                return;
            }
            if (e.getRawSlot() < 0 || e.getRawSlot() >= target.getInventory().getSize()) return;
            if (isTool(e.getCursor())) return;
            int slot = e.getRawSlot();
            ItemStack current = target.getInventory().getItem(slot);
            ItemStack cursor = e.getCursor();
            switch (e.getAction()) {
                case PICKUP_ALL, PICKUP_HALF -> {
                    if (current == null || !cursor.isEmpty()) return;
                    ItemStack taken = current.clone();
                    int amount = e.getAction() == InventoryAction.PICKUP_HALF ? (current.getAmount() + 1) / 2 : current.getAmount();
                    taken.setAmount(amount); current.setAmount(current.getAmount() - amount);
                    target.getInventory().setItem(slot, current.getAmount() == 0 ? null : current);
                    p.setItemOnCursor(taken);
                }
                case MOVE_TO_OTHER_INVENTORY -> {
                    if (isEnabled(p) || current == null) return;
                    Map<Integer, ItemStack> remaining = p.getInventory().addItem(current.clone());
                    target.getInventory().setItem(slot, remaining.get(0));
                }
                case HOTBAR_SWAP -> {
                    if (isEnabled(p) || e.getHotbarButton() < 0 || e.getHotbarButton() > 8) return;
                    ItemStack swapped = p.getInventory().getItem(e.getHotbarButton());
                    if (isTool(swapped)) return;
                    target.getInventory().setItem(slot, swapped == null ? null : swapped.clone());
                    p.getInventory().setItem(e.getHotbarButton(), current == null ? null : current.clone());
                }
                case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR -> {
                    if (cursor.isEmpty()) return;
                    if (current == null || current.isEmpty() || current.isSimilar(cursor)) {
                        int count = Math.min(e.getAction() == InventoryAction.PLACE_ONE ? 1 : cursor.getAmount(),
                                cursor.getMaxStackSize() - (current == null ? 0 : current.getAmount()));
                        if (count <= 0) return;
                        ItemStack placed = cursor.clone(); placed.setAmount(count + (current == null ? 0 : current.getAmount()));
                        target.getInventory().setItem(slot, placed);
                        cursor.setAmount(cursor.getAmount() - count); p.setItemOnCursor(cursor);
                    } else {
                        target.getInventory().setItem(slot, cursor.clone()); p.setItemOnCursor(current.clone());
                    }
                }
                default -> { return; }
            }
            copyInventory(menu, target);
            return;
        }
        if (isEnabled(p) || isTool(e.getCurrentItem()) || isTool(e.getCursor())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && (isFrozen(p) || isEnabled(p)
                || e.getView().getTopInventory().getHolder() instanceof Menu || isTool(e.getOldCursor()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !(e.getInventory().getHolder() instanceof Menu menu) || menu.target == null) return;
        ItemStack cursor = p.getItemOnCursor();
        if (cursor.isEmpty() || isTool(cursor)) return;
        // Cursor items belong to the inspection transaction, never to the staff loadout.
        if (isEnabled(p)) {
            Player target = Bukkit.getPlayer(menu.target);
            p.setItemOnCursor(null);
            if (target == null || target.isDead()) {
                Player location = target == null ? p : target;
                location.getWorld().dropItemNaturally(location.getLocation(), cursor.clone());
            } else {
                Map<Integer, ItemStack> remaining = target.getInventory().addItem(cursor.clone());
                for (ItemStack item : remaining.values()) target.getWorld().dropItemNaturally(target.getLocation(), item);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        if (isFrozen(e.getPlayer()) && e.hasChangedPosition()) e.setTo(e.getFrom());
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent e) {
        if (isFrozen(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!isFrozen(p)) return;
        String root = e.getMessage().substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        root = root.substring(root.indexOf(':') + 1);
        org.bukkit.command.PluginCommand command = Bukkit.getPluginCommand(root);
        String canonical = command == null ? root : command.getName();
        for (String blocked : Main.getInstance().getConfig().getStringList("freeze.blocked-commands")) {
            String name = blocked.replaceFirst("^/", "").toLowerCase(Locale.ROOT);
            if (root.equals(name) || canonical.equalsIgnoreCase(name)) { e.setCancelled(true); message(p, "freeze.frozen"); return; }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (isFrozen(e.getPlayer()) || isEnabled(e.getPlayer()) || isTool(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && (isFrozen(p) || isEnabled(p))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (isFrozen(e.getPlayer()) || isEnabled(e.getPlayer()) || isTool(e.getMainHandItem()) || isTool(e.getOffHandItem())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p && (isFrozen(p) || isEnabled(p) && !(e.getInventory().getHolder() instanceof Menu))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        if (isRestricted(e.getPlayer()) || isTool(e.getPlayer().getInventory().getItemInMainHand())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent e) {
        if (isRestricted(e.getPlayer()) || isTool(e.getItemInHand())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (isRestricted(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (isRestricted(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent e) {
        if (isRestricted(e.getPlayer()) || isTool(e.getItem())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && (isEnabled(p) || isFrozen(p))) e.setCancelled(true);
        Entity attacker = e.getDamageSource().getCausingEntity();
        if (attacker instanceof Player p && isRestricted(p)) e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent hit) {
            Entity source = hit.getDamager();
            if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) source = shooter;
            if (source instanceof Player p && isRestricted(p)) e.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof Player p && isRestricted(p)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFish(PlayerFishEvent e) {
        if (isRestricted(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVehicle(VehicleEnterEvent e) {
        if (e.getEntered() instanceof Player p && isRestricted(p)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() != null && isRestricted(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (e.getRemover() instanceof Player p && isRestricted(p)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBook(PlayerEditBookEvent e) {
        if (isRestricted(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSign(SignChangeEvent e) {
        if (isRestricted(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHarvest(PlayerHarvestBlockEvent e) {
        if (isRestricted(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDurability(PlayerItemDamageEvent e) {
        if (isTool(e.getItem())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        restore(p);
        for (Player target : Bukkit.getOnlinePlayers()) { visibility(p, target); visibility(target, p); }
        if (isFrozen(p)) { stop(p); message(p, "freeze.frozen"); }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (isVanished(p)) e.quitMessage(null);
        p.closeInventory();
        if (isEnabled(p)) toggle(p);
        vanished.remove(p.getUniqueId());
        vanishClicks.remove(p.getUniqueId());
        closeInspections(p);
    }

    private void closeInspections(Player p) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu
                    && p.getUniqueId().equals(menu.target)) viewer.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        if (isEnabled(e.getEntity()) || isFrozen(e.getEntity())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPick(io.papermc.paper.event.player.PlayerPickItemEvent e) {
        if (isFrozen(e.getPlayer()) || isEnabled(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (isRestricted(e.getPlayer()) || isEnabled(e.getPlayer()) && (isTool(e.getPlayerItem()) || isTool(e.getArmorStandItem()))) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShear(PlayerShearEntityEvent e) {
        if (isRestricted(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLectern(PlayerTakeLecternBookEvent e) {
        if (isRestricted(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSplash(PotionSplashEvent e) {
        if (e.getEntity().getShooter() instanceof Player p && isRestricted(p)) { e.setCancelled(true); return; }
        for (LivingEntity entity : e.getAffectedEntities()) {
            if (entity instanceof Player p && (isEnabled(p) || isFrozen(p))) e.setIntensity(entity, 0);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCloud(AreaEffectCloudApplyEvent e) {
        if (e.getEntity().getSource() instanceof Player p && isRestricted(p)) e.getAffectedEntities().clear();
        else e.getAffectedEntities().removeIf(entity -> entity instanceof Player p && (isEnabled(p) || isFrozen(p)));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent e) {
        Player p = e.getPlayer();
        if (!isEnabled(p)) return;
        boolean flying = p.isFlying();
        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            if (p.isOnline() && isEnabled(p)) {
                p.setAllowFlight(true);
                if (flying) p.setFlying(true);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExperience(PlayerExpChangeEvent e) {
        if (isFrozen(e.getPlayer()) || isEnabled(e.getPlayer())) e.setAmount(0);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMount(EntityMountEvent e) {
        if (e.getEntity() instanceof Player p && isRestricted(p)) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && (isFrozen(p) || isEnabled(p))) e.setCancelled(true);
    }
    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.closeInventory();
            if (isEnabled(p)) toggle(p);
            setVanished(p, false);
        }
        frozen.clear(); freezeAll = false;
    }
}
