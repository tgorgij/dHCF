package me.Thomas.HCF.Events.Player.Item;

import me.Thomas.HCF.IO.Claims.Claim;
import me.Thomas.HCF.IO.Claims.Claiming;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class UserClaimEvent implements Listener {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @EventHandler
    public void onLeftClickBlock(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action a = e.getAction();
        User user = User.get(p);
        ItemStack claimWand = Faction.getClaimWand();
        if (!a.equals(Action.LEFT_CLICK_BLOCK)) return;
        if (e.getItem() == null) return;
        if (!e.getItem().hasItemMeta()) return;
        if (!e.getItem().getItemMeta().hasDisplayName()) return;
        if (!e.getItem().getItemMeta().hasLore()) return;
        if (user.getClaiming() == null) return;
        if (!e.getItem().isSimilar(claimWand)) return;
        e.setCancelled(true);
        if (e.getClickedBlock() == null) return;

        Claiming claiming = user.getClaiming();

        if (claiming.getOne() != null) {
            resetPillar(claiming.getOne(), p);
        }

        if (claiming.getTwo() == null) {
            claiming.setOne(e.getClickedBlock().getLocation());
            generatePillar(claiming.getPillar(), e.getClickedBlock().getLocation(), p);
            p.sendMessage(C.chat(localeConfig.getString("claiming.success.one")));
            return;
        }
        Location one = e.getClickedBlock().getLocation();
        Location two = claiming.getTwo();
        if (!one.getWorld().equals(two.getWorld())) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.world")));
            return;
        }

        Claim claim = new Claim(claiming.getFaction(), one, two);
        if (claim.intersects()) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.intersects")));
            return;
        }

        claiming.setOne(e.getClickedBlock().getLocation());
        generatePillar(claiming.getPillar(), e.getClickedBlock().getLocation(), p);
        p.sendMessage(C.chat(localeConfig.getString("claiming.success.one")));
    }

    @EventHandler
    public void onRightClickBlock(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action a = e.getAction();
        User user = User.get(p);
        ItemStack claimWand = Faction.getClaimWand();
        if (!a.equals(Action.RIGHT_CLICK_BLOCK)) return;
        if (e.getItem() == null) return;
        if (!e.getItem().hasItemMeta()) return;
        if (!e.getItem().getItemMeta().hasDisplayName()) return;
        if (!e.getItem().getItemMeta().hasLore()) return;
        if (user.getClaiming() == null) return;
        if (!e.getItem().isSimilar(claimWand)) return;
        e.setCancelled(true);
        if (e.getClickedBlock() == null) return;

        Claiming claiming = user.getClaiming();

        if (claiming.getTwo() != null) {
            resetPillar(claiming.getTwo(), p);
        }

        if (claiming.getOne() == null) {
            claiming.setTwo(e.getClickedBlock().getLocation());
            generatePillar(claiming.getPillar(), e.getClickedBlock().getLocation(), p);
            p.sendMessage(C.chat(localeConfig.getString("claiming.success.two")));
            return;
        }
        Location one = claiming.getOne();
        Location two = e.getClickedBlock().getLocation();
        if (!one.getWorld().equals(two.getWorld())) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.world")));
            return;
        }

        Claim claim = new Claim(claiming.getFaction(), one, two);
        if (claim.intersects()) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.intersects")));
            return;
        }

        claiming.setTwo(e.getClickedBlock().getLocation());
        generatePillar(claiming.getPillar(), e.getClickedBlock().getLocation(), p);
        p.sendMessage(C.chat(localeConfig.getString("claiming.success.two")));
    }

    @EventHandler
    public void onLeftClickCrouchAir(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action a = e.getAction();
        User user = User.get(p);
        ItemStack claimWand = Faction.getClaimWand();
        if (!a.equals(Action.LEFT_CLICK_AIR) && !p.isSneaking()) return;
        if (e.getItem() == null) return;
        if (!e.getItem().hasItemMeta()) return;
        if (!e.getItem().getItemMeta().hasDisplayName()) return;
        if (!e.getItem().getItemMeta().hasLore()) return;
        if (user.getClaiming() == null) return;
        if (!e.getItem().isSimilar(claimWand)) return;
        e.setCancelled(true);

        Claiming claiming = user.getClaiming();

        if (claiming.getOne() == null || claiming.getTwo() == null) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.both")));
            return;
        }

        Location one = claiming.getOne();
        Location two = claiming.getTwo();
        if (!one.getWorld().equals(two.getWorld())) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.world")));
            return;
        }

        Claim claim = new Claim(claiming.getFaction(), one, two);
        if (claim.intersects()) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.intersects")));
            return;
        }

        Main.getInstance().addClaim(claiming.getFaction(), one, two);
        p.sendMessage(C.chat(localeConfig.getString("claiming.success.claim")));
        user.setClaiming(null);
        p.getInventory().remove(claimWand);
    }

    private void generatePillar(Material material, Location location, Player p) {

        new BukkitRunnable() {
            @Override
            public void run() {
                World world = location.getWorld();
                for (int i = world.getMinHeight(); i < world.getMaxHeight(); i++) {
                    Location loc = new Location(world, location.getBlockX(), i, location.getBlockZ());
                    int block = i % 4;
                    if (!world.getBlockAt(loc).isEmpty()) continue;
                    if (block == 0) {
                        p.sendBlockChange(loc, material.createBlockData());
                    } else {
                        p.sendBlockChange(loc, Material.GLASS.createBlockData());
                    }
                }
            }
        }.runTaskLater(Main.getInstance(), 1);
    }

    private void resetPillar(Location location, Player p) {
        World world = location.getWorld();
        for (int i = world.getMinHeight(); i < world.getMaxHeight(); i++) {
            Location loc = new Location(world, location.getBlockX(), i, location.getBlockZ());
            if (!world.getBlockAt(loc).isEmpty()) continue;
            p.sendBlockChange(loc, Material.AIR.createBlockData());
        }
    }
}
