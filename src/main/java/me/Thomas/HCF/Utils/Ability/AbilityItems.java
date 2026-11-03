package me.Thomas.HCF.Utils.Ability;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Items.Items;
import me.Thomas.HCF.Utils.Timer.ServerTimer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;

public class AbilityItems {

    public enum Type {
        SWITCHER("switcher", Material.SNOWBALL),
        BONE("bone", Material.BONE),
        RAGE("rage", Material.EGG),
        NINJA("ninja", Material.NETHER_STAR),
        ARCHER("archer", Material.BOW),
        BRICK("brick", Material.BRICK),
        SWITCHSTICK("switchstick", Material.STICK),
        SECONDCHANCE("secondchance", Material.CLOCK),
        POTIONCOUNTER("potioncounter", Material.BLAZE_ROD),
        STARVATION("starvation", Material.ROTTEN_FLESH);

        private final String id;
        private final Material material;

        Type(String id, Material material) { this.id = id; this.material = material; }
        public String getId() { return id; }
        public String path() { return "abilities.items." + id; }
        public String getName() { return Main.getInstance().getConfig().getString(path() + ".name", id); }
    }

    public static NamespacedKey key(String key) { return new NamespacedKey(Main.getInstance(), "ability-" + key); }

    public static Type getType(ItemStack item) {
        if (item == null || item.isEmpty() || !item.hasItemMeta()) return null;
        return getType(item.getItemMeta().getPersistentDataContainer().get(key("type"), PersistentDataType.STRING));
    }

    public static Type getType(String id) {
        if (id == null) return null;
        for (Type type : Type.values()) if (type.id.equalsIgnoreCase(id)) return type;
        return null;
    }

    public static ItemStack create(Type type) {
        int uses = Math.max(1, Main.getInstance().getConfig().getInt(type.path() + ".uses", 5));
        ItemStack item = Items.create("abilities." + type.id, type.material, Map.of("%uses%", Integer.toString(uses)));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key("type"), PersistentDataType.STRING, type.id);
        if (type == Type.ARCHER) {
            // A charged bow has its own identity, even after it is moved between slots.
            meta.setMaxStackSize(1);
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(key("identity"), PersistentDataType.STRING, UUID.randomUUID().toString());
            meta.getPersistentDataContainer().set(key("uses"), PersistentDataType.INTEGER, uses);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static Map<String, ServerTimer> timers(Player p) {
        Map<String, ServerTimer> timers = User.get(p).getAbilityTimers();
        timers.values().removeIf(timer -> timer.getRemainingTimeMillis() <= 0);
        return timers;
    }

    public static boolean ready(Player p, Type type, boolean notify) {
        Map<String, ServerTimer> timers = timers(p);
        ServerTimer timer = timers.get("global");
        ServerTimer local = timers.get(type.id);
        if (local != null && (timer == null || local.getRemainingTimeMillis() > timer.getRemainingTimeMillis())) timer = local;
        if (timer == null) return true;
        if (notify) message(p, "cooldown", "%time%", C.formatChatTime((timer.getRemainingTimeMillis() + 999) / 1000));
        return false;
    }

    public static void cooldown(Player p, Type type) {
        int global = Math.clamp(Main.getInstance().getConfig().getInt("abilities.global-cooldown", 15), 0, 86400);
        int local = Math.clamp(Main.getInstance().getConfig().getInt(type.path() + ".cooldown", 60), 0, 86400);
        if (global > 0) timers(p).put("global", new ServerTimer(Main.getInstance().getConfig().getString("abilities.global-name", "&dAbility"), global));
        if (local > 0) timers(p).put(type.id, new ServerTimer(type.getName(), local));
    }

    public static void message(Player p, String key, String... replacements) {
        String message = Main.getInstance().getLocaleConfig().getString("abilities." + key, "");
        for (int i = 0; i + 1 < replacements.length; i += 2) message = message.replace(replacements[i], replacements[i + 1]);
        if (!message.isEmpty()) p.sendMessage(C.chat(message));
    }
}
