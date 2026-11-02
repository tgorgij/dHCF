package me.Thomas.HCF.Events.Player.Interact;

import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Rewards.TechTree;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class UserRewardsEvent implements Listener {
    private static final int VISIBLE = 7;

    private static class Menu implements InventoryHolder {
        private final UUID owner;
        private final UUID faction;
        private String tree;
        private int offset;
        private Inventory inventory;
        private Menu(Player p, String tree) { this.owner = p.getUniqueId(); this.faction = TechTree.faction(p).getUUID(); this.tree = tree; }
        @Override public @NotNull Inventory getInventory() { return inventory; }
    }

    private final BukkitTask task;

    public UserRewardsEvent() {
        task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu)) continue;
                String denied = validate(p, menu);
                if (denied != null) { p.closeInventory(); message(p, denied); }
                else render(menu, TechTree.faction(p));
            }
        }, 20L, 20L);
    }

    public static void message(Player p, String key) {
        p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.rewards." + key)));
    }

    public static void open(Player p) {
        List<TechTree.Tree> trees = TechTree.get().getTrees();
        if (trees.isEmpty()) { message(p, "unavailable"); return; }
        Menu menu = new Menu(p, trees.getFirst().id());
        menu.inventory = Bukkit.createInventory(menu, 54, C.chat(Main.getInstance().getGuiConfig().getString("rewards.title")
                .replace("%faction%", TechTree.faction(p).getName())));
        render(menu, TechTree.faction(p));
        p.openInventory(menu.inventory);
    }

    private static String validate(Player p, Menu menu) {
        String denied = TechTree.access(p);
        if (denied != null) return denied;
        return menu.owner.equals(p.getUniqueId()) && menu.faction.equals(TechTree.faction(p).getUUID()) ? null : "no-faction";
    }

    private static String label(TechTree.Tree tree, TechTree.Node node) {
        return Main.getInstance().getGuiConfig().getString("rewards.nodes." + tree.id() + "." + node.id() + ".label", node.id());
    }

    private static ItemStack item(String path, String fallbackPath, Material fallback, Map<String, String> replacements) {
        FileConfiguration gui = Main.getInstance().getGuiConfig();
        Material material = Material.matchMaterial(gui.getString(path + ".material", gui.getString(fallbackPath + ".material", fallback.name())));
        ItemStack item = new ItemStack(material == null || material.isAir() || !material.isItem() ? fallback : material);
        ItemMeta meta = item.getItemMeta();
        String name = gui.getString(path + ".name", gui.getString(fallbackPath + ".name", ""));
        for (Map.Entry<String, String> entry : replacements.entrySet()) name = name.replace(entry.getKey(), entry.getValue());
        meta.displayName(C.chat(name).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        List<String> configured = gui.isList(path + ".lore") ? gui.getStringList(path + ".lore") : gui.getStringList(fallbackPath + ".lore");
        for (String line : configured) {
            for (Map.Entry<String, String> entry : replacements.entrySet()) line = line.replace(entry.getKey(), entry.getValue());
            lore.add(C.chat(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static void render(Menu menu, Faction faction) {
        FileConfiguration gui = Main.getInstance().getGuiConfig();
        TechTree tech = TechTree.get();
        TechTree.Tree tree = tech.getTree(menu.tree);
        if (tree == null) return;
        menu.offset = Math.clamp(menu.offset, 0, Math.max(0, tree.nodes().size() - VISIBLE));
        menu.inventory.clear();
        for (int i = 0; i < tech.getTrees().size(); i++) {
            TechTree.Tree choice = tech.getTrees().get(i);
            menu.inventory.setItem(i, item("rewards.trees." + choice.id(), "rewards.tree", Material.BOOK,
                    Map.of("%tree%", choice.id(), "%state%", choice.id().equals(menu.tree) ? gui.getString("rewards.selected") : "")));
        }
        for (int column = 0; column < VISIBLE && menu.offset + column < tree.nodes().size(); column++) {
            TechTree.Node node = tree.nodes().get(menu.offset + column);
            String state = tech.isUnlocked(faction, tree, node) ? "unlocked" : tech.prerequisites(faction, tree, node)
                    && tech.progress(faction, node) >= node.amount() ? "available" : "locked";
            String requirement = gui.getString("rewards.requirements." + node.requirement().name().toLowerCase(java.util.Locale.ROOT));
            menu.inventory.setItem(19 + column, item("rewards.nodes." + tree.id() + "." + node.id(), "rewards.node", Material.PAPER,
                    Map.of("%node%", label(tree, node), "%state%", gui.getString("rewards.states." + state),
                            "%requirement%", requirement, "%amount%", Integer.toString(node.amount()), "%progress%", Integer.toString(tech.progress(faction, node)))));
        }
        String previous = menu.offset == 0 ? "start" : "previous", next = menu.offset >= Math.max(0, tree.nodes().size() - VISIBLE) ? "end" : "next";
        menu.inventory.setItem(45, item("rewards." + previous, "rewards.previous", Material.ARROW, Map.of()));
        menu.inventory.setItem(53, item("rewards." + next, "rewards.next", Material.ARROW, Map.of()));
        menu.inventory.setItem(49, item("rewards.summary", "rewards.summary", Material.NETHER_STAR,
                Map.of("%points%", Integer.toString(faction.getPoints()), "%kills%", Integer.toString(faction.getKills()), "%captures%", Integer.toString(faction.getKOTHCaptures()))));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (Main.getInstance().getStaffMode().isFrozen(p)) return;
        String denied = validate(p, menu);
        if (denied != null) {
            message(p, denied);
            Bukkit.getScheduler().runTask(Main.getInstance(), () -> { if (p.getOpenInventory().getTopInventory() == menu.inventory) p.closeInventory(); });
            return;
        }
        if (e.getClick() != ClickType.LEFT && e.getClick() != ClickType.RIGHT) return;
        TechTree tech = TechTree.get();
        int slot = e.getRawSlot();
        if (slot >= 0 && slot < tech.getTrees().size()) { menu.tree = tech.getTrees().get(slot).id(); menu.offset = 0; }
        else if (slot == 45) menu.offset = Math.max(0, menu.offset - 1);
        else if (slot == 53) menu.offset = Math.min(Math.max(0, tech.getTree(menu.tree).nodes().size() - VISIBLE), menu.offset + 1);
        else if (slot >= 19 && slot <= 25) {
            TechTree.Tree tree = tech.getTree(menu.tree);
            int index = menu.offset + slot - 19;
            if (index >= tree.nodes().size()) return;
            TechTree.Node node = tree.nodes().get(index);
            try {
                tech.unlock(p, tree.id(), node.id());
                p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.rewards.unlocked").replace("%node%", label(tree, node))));
            } catch (IllegalArgumentException exception) { message(p, exception.getMessage()); }
            catch (RuntimeException exception) {
                Main.getInstance().getLogger().log(Level.SEVERE, "Could not unlock faction reward", exception);
                message(p, "failed");
            }
        } else return;
        render(menu, TechTree.faction(p));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    @EventHandler
    public void onDisable(PluginDisableEvent e) {
        if (e.getPlugin() != Main.getInstance()) return;
        task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu) p.closeInventory();
    }
}
