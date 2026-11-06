package me.Thomas.HCF.Events.Player.Interact;

import io.papermc.paper.event.player.PlayerOpenSignEvent;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class UserKitEvent implements Listener {
    private record Loadout(ItemStack[] contents, ItemStack[] armor) {}

    private static NamespacedKey key(Side side) {
        return new NamespacedKey(Main.getInstance(), "kit-" + side.name().toLowerCase(Locale.ROOT));
    }

    private static String plain(Component line) {
        return line == null ? "" : PlainTextComponentSerializer.plainText().serialize(line).trim();
    }

    private static boolean isKitSign(Sign sign) {
        for (Side side : Side.values()) if (sign.getPersistentDataContainer().has(key(side))) return true;
        return false;
    }

    private static boolean admin(Player player) { return Permission.has(player, "sign.kit.create", "admin"); }

    private static String name(String kit) {
        return Main.getInstance().getKitsConfig().getString("kits." + kit + ".name", kit);
    }

    private static ItemStack item(ConfigurationSection section) {
        if (section == null) throw new IllegalArgumentException("Missing kit item section");
        Material material = Material.matchMaterial(section.getString("material", ""));
        if (material == null || material.isAir() || !material.isItem()) {
            throw new IllegalArgumentException("Invalid material at " + section.getCurrentPath());
        }
        ItemStack item = new ItemStack(material);
        int amount = section.getInt("amount", 1);
        if (amount < 1 || amount > item.getMaxStackSize()) throw new IllegalArgumentException("Invalid amount at " + section.getCurrentPath());
        item.setAmount(amount);
        if (!item.editMeta(meta -> {
            if (section.isString("name")) meta.displayName(C.chat(section.getString("name", "")).decoration(TextDecoration.ITALIC, false));
            if (section.isList("lore")) meta.lore(section.getStringList("lore").stream()
                    .map(line -> C.chat(line).decoration(TextDecoration.ITALIC, false)).toList());
            ConfigurationSection enchantments = section.getConfigurationSection("enchantments");
            if (enchantments != null) for (String enchant : enchantments.getKeys(false)) {
                NamespacedKey key = NamespacedKey.fromString(enchant.toLowerCase(Locale.ROOT));
                Enchantment type = key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
                int level = enchantments.getInt(enchant, 0);
                if (type == null || level < 1 || level > 255) throw new IllegalArgumentException("Invalid enchantment at " + enchantments.getCurrentPath() + "." + enchant);
                meta.addEnchant(type, level, true);
            }
            if (section.isSet("potion")) {
                if (!(meta instanceof PotionMeta potion)) throw new IllegalArgumentException("Expected potion material at " + section.getCurrentPath());
                potion.setBasePotionType(PotionType.valueOf(section.getString("potion", "").toUpperCase(Locale.ROOT)));
            }
        })) throw new IllegalArgumentException("Item has no metadata at " + section.getCurrentPath());
        return item;
    }

    private static Loadout load(String kit) {
        ConfigurationSection configuration = Main.getInstance().getKitsConfig().getConfigurationSection("kits." + kit);
        if (configuration == null) throw new IllegalArgumentException("Unknown kit: " + kit);
        ItemStack[] armor = new ItemStack[4];
        List<String> armorSlots = List.of("boots", "leggings", "chestplate", "helmet");
        for (int slot = 0; slot < armorSlots.size(); slot++) armor[slot] = item(configuration.getConfigurationSection("armor." + armorSlots.get(slot)));
        ItemStack[] contents = new ItemStack[36];
        ConfigurationSection items = configuration.getConfigurationSection("items");
        if (items == null) throw new IllegalArgumentException("Missing kit items: " + kit);
        for (String key : items.getKeys(false)) {
            int slot = Integer.parseInt(key);
            if (slot < 0 || slot >= contents.length || contents[slot] != null) throw new IllegalArgumentException("Invalid inventory slot: " + key);
            contents[slot] = item(items.getConfigurationSection(key));
        }
        // Fill after reserving every configured item slot so class supplies are never overwritten.
        ItemStack fill = item(configuration.getConfigurationSection("fill"));
        for (int slot = 0; slot < contents.length; slot++) if (contents[slot] == null) contents[slot] = fill.clone();
        return new Loadout(contents, armor);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent e) {
        if (!(e.getBlock().getState() instanceof Sign sign)) return;
        if (isKitSign(sign)) { e.setCancelled(true); return; }
        if (!plain(e.line(0)).equalsIgnoreCase("[Kit]")) return;
        if (!admin(e.getPlayer())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return;
        }
        if (!Main.getInstance().isKitMap()) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.feature-disabled")));
            return;
        }
        String kit = plain(e.line(1)).toLowerCase(Locale.ROOT);
        if (!Main.getInstance().getKitsConfig().isConfigurationSection("kits." + kit)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.kit.invalid")));
            return;
        }
        try { load(kit); }
        catch (IllegalArgumentException exception) {
            e.setCancelled(true);
            Main.getInstance().getLogger().log(Level.WARNING, "Invalid kit " + kit, exception);
            e.getPlayer().sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.kit.configuration-error")));
            return;
        }
        e.line(0, C.chat("&9[Kit]"));
        e.line(1, Component.text(kit));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChanged(SignChangeEvent e) {
        if (!Main.getInstance().isKitMap() || !admin(e.getPlayer()) || !plain(e.line(0)).equalsIgnoreCase("[Kit]")
                || !(e.getBlock().getState() instanceof Sign sign)) return;
        String kit = plain(e.line(1)).toLowerCase(Locale.ROOT);
        if (!Main.getInstance().getKitsConfig().isConfigurationSection("kits." + kit)) return;
        e.line(1, C.chat(name(kit)));
        sign.getPersistentDataContainer().set(key(e.getSide()), PersistentDataType.STRING, kit);
        sign.setWaxed(true);
        sign.setAllowedEditorUniqueId(null);
        sign.update();
        e.getPlayer().sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.kit.created").replace("%kit%", name(kit))));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!(e.getBlockPlaced().getState() instanceof Sign sign)) return;
        for (Side side : Side.values()) sign.getPersistentDataContainer().remove(key(side));
        sign.update();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignOpen(PlayerOpenSignEvent e) {
        if (!isKitSign(e.getSign())) return;
        e.setCancelled(true);
        e.getSign().setWaxed(true);
        e.getSign().setAllowedEditorUniqueId(null);
        e.getSign().update();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND
                || e.useInteractedBlock() == Event.Result.DENY || e.getClickedBlock() == null
                || !(e.getClickedBlock().getState() instanceof Sign sign)) return;
        Player player = e.getPlayer();
        String kit = sign.getPersistentDataContainer().get(key(sign.getInteractableSideFor(player)), PersistentDataType.STRING);
        if (kit == null) return;
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        if (!Main.getInstance().isKitMap()) {
            player.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.feature-disabled")));
            return;
        }
        if (player.isDead()) return;
        if (User.get(player).isInStaffMode() || Main.getInstance().getStaffMode() != null && Main.getInstance().getStaffMode().isFrozen(player)) {
            player.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.kit.unavailable")));
            return;
        }
        Loadout loadout;
        try { loadout = load(kit); }
        catch (IllegalArgumentException exception) {
            Main.getInstance().getLogger().log(Level.WARNING, "Could not give kit " + kit, exception);
            player.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.kit.configuration-error")));
            return;
        }
        player.getInventory().clear();
        player.getInventory().setArmorContents(loadout.armor());
        player.getInventory().setStorageContents(loadout.contents());
        player.getInventory().setItemInOffHand(null);
        player.setItemOnCursor(null);
        player.getInventory().setHeldItemSlot(0);
        player.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("events.player.kit.received").replace("%kit%", name(kit))));
    }
}
