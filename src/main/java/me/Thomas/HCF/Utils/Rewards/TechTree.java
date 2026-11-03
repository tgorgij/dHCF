package me.Thomas.HCF.Utils.Rewards;

import me.Thomas.HCF.IO.Database.SaveSnapshot;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Timer.Timer;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TechTree {
    public enum Requirement { KILLS, CAPTURES, POINTS }
    public enum Reward { POTION, ENCHANTMENT, COMMAND }
    public record Node(String id, Requirement requirement, int amount, Reward reward, String value, int level) {}
    public record Tree(String id, List<Node> nodes) {}

    private final Map<String, Tree> trees = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> unlocked = new HashMap<>();

    public TechTree() {
        org.bukkit.configuration.file.FileConfiguration config = Main.getInstance().getConfig();
        ConfigurationSection configuration = config.isSet("tech-tree.trees") ? config.getConfigurationSection("tech-tree.trees")
                : config.getDefaults() == null ? null : config.getDefaults().getConfigurationSection("tech-tree.trees");
        if (configuration != null) for (String id : configuration.getKeys(false)) {
            if (!id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid tech tree ID: " + id);
            ConfigurationSection section = configuration.getConfigurationSection(id);
            Requirement requirement = Requirement.valueOf(section.getString("requirement").toUpperCase(Locale.ROOT));
            ConfigurationSection entries = section.getConfigurationSection("nodes");
            if (entries == null) throw new IllegalArgumentException("Missing tech tree nodes: " + id);
            List<Node> nodes = new ArrayList<>();
            for (String name : entries.getKeys(false)) {
                if (!name.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid tech node ID: " + name);
                ConfigurationSection entry = entries.getConfigurationSection(name);
                Reward reward = Reward.valueOf(entry.getString("type").toUpperCase(Locale.ROOT));
                String configuredValue = entry.getString("value").toLowerCase(Locale.ROOT);
                String value = reward == Reward.COMMAND ? configuredValue : configuredValue.replaceFirst("^minecraft:", "");
                int amount = entry.getInt("amount", -1), level = entry.getInt("level", 1);
                if (amount < 0 || level < 1 || level > 255) throw new IllegalArgumentException("Invalid tech node: " + name);
                NamespacedKey key = NamespacedKey.fromString(value);
                if (reward == Reward.ENCHANTMENT && (key == null || Registry.ENCHANTMENT.get(key) == null)
                        || reward == Reward.POTION && java.util.Arrays.stream(PotionType.values()).noneMatch(type -> type.getKey().getKey().equals(value))
                        || reward == Reward.COMMAND && !Set.of("craft", "bottle").contains(value)) {
                    throw new IllegalArgumentException("Invalid tech reward: " + value);
                }
                nodes.add(new Node(name, requirement, amount, reward, value, level));
            }
            trees.put(id, new Tree(id, List.copyOf(nodes)));
        }
        if (trees.size() > 9) throw new IllegalArgumentException("The rewards menu supports up to nine tech trees");
        try (Connection connection = Main.getInstance().getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT faction, tree, node FROM factionTechUnlocks");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) unlocked.computeIfAbsent(UUID.fromString(rows.getString("faction")), ignored -> new HashSet<>())
                    .add(rows.getString("tree") + "/" + rows.getString("node"));
        } catch (SQLException exception) { throw new IllegalStateException("Could not load faction tech unlocks", exception); }
    }

    public static TechTree get() { return Main.getInstance().getTechTree(); }
    public List<Tree> getTrees() { return List.copyOf(trees.values()); }
    public Tree getTree(String id) { return trees.get(id); }
    public void forgetFaction(UUID uuid) { unlocked.remove(uuid); }

    public static Faction faction(Player p) {
        User user = User.get(p);
        Faction faction = user != null && user.hasFaction() ? Faction.get(user.getFaction()) : null;
        return faction != null && faction.getType() == Faction.Type.PLAYER && faction.hasMember(p.getUniqueId()) ? faction : null;
    }

    public static String access(Player p) {
        if (User.get(p).hasTimer(Timer.Type.COMBAT)) return "combat";
        Faction faction = faction(p);
        if (faction == null) return "no-faction";
        Faction territory = Faction.getAtLocation(p.getLocation());
        if (territory == null || territory.getType() != Faction.Type.SAFEZONE && !territory.getUUID().equals(faction.getUUID())) return "location";
        return null;
    }

    private boolean raw(Faction faction, Tree tree, Node node) {
        return unlocked.getOrDefault(faction.getUUID(), Set.of()).contains(tree.id() + "/" + node.id());
    }

    public boolean isUnlocked(Faction faction, Tree tree, Node node) {
        if (faction == null) return false;
        for (Node previous : tree.nodes()) {
            if (!raw(faction, tree, previous)) return false;
            if (previous.id().equals(node.id())) return true;
        }
        return false;
    }

    public boolean prerequisites(Faction faction, Tree tree, Node node) {
        for (Node previous : tree.nodes()) {
            if (previous.id().equals(node.id())) return true;
            if (!raw(faction, tree, previous)) return false;
        }
        return false;
    }

    public int progress(Faction faction, Node node) {
        return switch (node.requirement()) {
            case KILLS -> faction.getKills();
            case CAPTURES -> faction.getEventCaptures();
            case POINTS -> faction.getPoints();
        };
    }

    public void unlock(Player p, String treeId, String nodeId) {
        String denied = access(p);
        if (denied != null) throw new IllegalArgumentException(denied);
        Faction faction = faction(p);
        Tree tree = trees.get(treeId);
        Node node = tree == null ? null : tree.nodes().stream().filter(candidate -> candidate.id().equals(nodeId)).findFirst().orElse(null);
        if (node == null) throw new IllegalArgumentException("unavailable");
        if (raw(faction, tree, node)) throw new IllegalArgumentException("already");
        if (!prerequisites(faction, tree, node)) throw new IllegalArgumentException("previous");
        if (node.requirement() == Requirement.POINTS && faction.getMemberType(p.getUniqueId()).getValue() < Faction.MemberType.COLEADER.getValue()) {
            throw new IllegalArgumentException("rank");
        }
        if (progress(faction, node) < node.amount()) throw new IllegalArgumentException("requirement");
        int previousPoints = faction.getPoints();
        if (node.requirement() == Requirement.POINTS) faction.setPoints(previousPoints - node.amount());
        List<SaveSnapshot> snapshots = new ArrayList<>();
        SaveSnapshot factionSnapshot = faction.snapshot();
        if (factionSnapshot != null) snapshots.add(factionSnapshot);
        snapshots.add(new SaveSnapshot("INSERT INTO factionTechUnlocks (faction, tree, node) VALUES (?, ?, ?)", () -> {},
                faction.getUUID().toString(), tree.id(), node.id()));
        try { Main.getInstance().getDatabaseManager().save(snapshots).join(); }
        catch (RuntimeException exception) { faction.setPoints(previousPoints); throw exception; }
        unlocked.computeIfAbsent(faction.getUUID(), ignored -> new HashSet<>()).add(tree.id() + "/" + node.id());
    }

    public boolean hasPotion(Faction faction, String name) {
        if (faction == null) return false;
        for (Tree tree : trees.values()) for (Node node : tree.nodes()) {
            if (node.reward() == Reward.POTION && node.value().equals(name) && isUnlocked(faction, tree, node)) return true;
        }
        return false;
    }

    public int enchantmentLimit(Player p, Enchantment enchantment) {
        int limit = Main.getInstance().getConfig().getInt("enchantments." + enchantment.getKey().getKey(), -1);
        if (limit < 0) return limit;
        Faction faction = faction(p);
        if (faction == null) return limit;
        for (Tree tree : trees.values()) for (Node node : tree.nodes()) {
            if (node.reward() == Reward.ENCHANTMENT && node.value().equals(enchantment.getKey().getKey()) && isUnlocked(faction, tree, node)) {
                limit = Math.max(limit, node.level());
            }
        }
        return limit;
    }

    public static boolean canUseCommand(Player p, String command) {
        TechTree tech = get();
        Faction faction = faction(p);
        if (tech == null || faction == null) return false;
        for (Tree tree : tech.trees.values()) for (Node node : tree.nodes()) {
            if (node.reward() == Reward.COMMAND && node.value().equals(command) && tech.isUnlocked(faction, tree, node)) return true;
        }
        return false;
    }

    public static String potionName(PotionType type) {
        String name = type.getKey().getKey();
        return name.startsWith("long_") ? name.substring(5) : name.startsWith("strong_") ? name.substring(7) : name;
    }

    private static NamespacedKey potionKey() { return new NamespacedKey(Main.getInstance(), "tech-potion"); }
    private static NamespacedKey enchantKey() { return new NamespacedKey(Main.getInstance(), "tech-enchantments"); }

    public static boolean producedPotion(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof PotionMeta meta) || meta.getBasePotionType() == null) return false;
        String authorization = meta.getPersistentDataContainer().get(potionKey(), PersistentDataType.STRING);
        // Older items authorized the whole effect; retain those items, but never carry that grant into an upgrade.
        return ("variant:" + meta.getBasePotionType().getKey().getKey()).equals(authorization)
                || potionName(meta.getBasePotionType()).equals(authorization);
    }

    public static void markPotion(ItemStack item) {
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.getPersistentDataContainer().set(potionKey(), PersistentDataType.STRING, "variant:" + meta.getBasePotionType().getKey().getKey());
        item.setItemMeta(meta);
    }

    public static void clearPotion(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(potionKey());
        item.setItemMeta(meta);
    }

    public static Map<Enchantment, Integer> enchants(ItemMeta meta) {
        Map<Enchantment, Integer> result = new HashMap<>(meta.getEnchants());
        if (meta instanceof EnchantmentStorageMeta book) book.getStoredEnchants().forEach((enchantment, level) -> result.merge(enchantment, level, Math::max));
        return result;
    }

    public static int producedEnchantment(ItemStack item, Enchantment enchantment) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        String data = meta.getPersistentDataContainer().get(enchantKey(), PersistentDataType.STRING);
        if (data == null) return 0;
        for (String entry : data.split(";")) {
            String[] parts = entry.split("=");
            if (parts.length != 2 || !parts[0].equals(enchantment.getKey().toString())) continue;
            try { return Math.max(0, Math.min(Integer.parseInt(parts[1]), enchants(meta).getOrDefault(enchantment, 0))); }
            catch (NumberFormatException exception) { return 0; }
        }
        return 0;
    }

    public static void markEnchantments(ItemMeta meta, Map<Enchantment, Integer> authorized) {
        StringBuilder data = new StringBuilder();
        authorized.forEach((enchantment, level) -> data.append(enchantment.getKey()).append('=').append(level).append(';'));
        if (data.isEmpty()) meta.getPersistentDataContainer().remove(enchantKey());
        else meta.getPersistentDataContainer().set(enchantKey(), PersistentDataType.STRING, data.toString());
    }
}
