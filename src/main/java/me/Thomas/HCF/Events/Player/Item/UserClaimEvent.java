package me.Thomas.HCF.Events.Player.Item;

import me.Thomas.HCF.IO.Claims.Claim;
import me.Thomas.HCF.IO.Claims.Claiming;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

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
        if (user.getFaction() == null) {
            user.setClaiming(null);
            p.getInventory().remove(claimWand);
            return;
        }
        Faction faction = Faction.get(user.getFaction());
        if (faction == null) {
            user.setClaiming(null);
            p.getInventory().remove(claimWand);
            return;
        }
        if (e.getClickedBlock() == null) return;

        Claiming claiming = user.getClaiming();

        if (!claiming.isClaimingFor()) {
            if (!Faction.isInWilderness(e.getClickedBlock().getLocation())) {
                p.sendMessage(C.chat(localeConfig.getString("claiming.error.not-in-wilderness")));
                return;
            }
        }

        if (claiming.getTwo() == null) {
            if (claiming.getOne() != null) Claiming.resetPillar(claiming.getOne(), p);
            claiming.setOne(e.getClickedBlock().getLocation());
            Claiming.generatePillar(claiming.getPillar(), e.getClickedBlock().getLocation(), p);
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
        if (invalidSize(p, claim)) return;
        if (claim.intersects()) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.intersects")));
            return;
        }

        Claiming.resetSelectionPillars(claiming, p);
        claiming.setOne(e.getClickedBlock().getLocation());
        Claiming.generateClaimPillars(claim, claiming.getPillar(), p);
        p.sendMessage(C.chat(localeConfig.getString("claiming.success.one")));
        p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("claiming.purchase.cost")).replace("%cost%", C.getFormattedMoney(claim.price())).replace("%size-x%", claim.width() + "").replace("%size-z%", claim.length() + "")));

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
        if (user.getFaction() == null) {
            user.setClaiming(null);
            p.getInventory().remove(claimWand);
            return;
        }
        Faction faction = Faction.get(user.getFaction());
        if (faction == null) {
            user.setClaiming(null);
            p.getInventory().remove(claimWand);
            return;
        }

        if (e.getClickedBlock() == null) return;

        Claiming claiming = user.getClaiming();

        if (!claiming.isClaimingFor()) {
            if (!Faction.isInWilderness(e.getClickedBlock().getLocation())) {
                p.sendMessage(C.chat(localeConfig.getString("claiming.error.not-in-wilderness")));
                return;
            }
        }

        if (claiming.getOne() == null) {
            if (claiming.getTwo() != null) Claiming.resetPillar(claiming.getTwo(), p);
            claiming.setTwo(e.getClickedBlock().getLocation());
            Claiming.generatePillar(claiming.getPillar(), e.getClickedBlock().getLocation(), p);
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
        if (invalidSize(p, claim)) return;
        if (claim.intersects()) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.intersects")));
            return;
        }

        Claiming.resetSelectionPillars(claiming, p);
        claiming.setTwo(e.getClickedBlock().getLocation());
        Claiming.generateClaimPillars(claim, claiming.getPillar(), p);
        p.sendMessage(C.chat(localeConfig.getString("claiming.success.two")));

        p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("claiming.purchase.cost")).replace("%cost%", C.getFormattedMoney(claim.price())).replace("%size-x%", claim.width() + "").replace("%size-z%", claim.length() + "")));
    }

    @EventHandler
    public void onRightClickAir(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action a = e.getAction();
        User user = User.get(p);
        ItemStack claimWand = Faction.getClaimWand();
        if (!a.equals(Action.RIGHT_CLICK_AIR) || !p.isSneaking()) return;
        if (e.getItem() == null) return;
        if (!e.getItem().hasItemMeta()) return;
        if (!e.getItem().getItemMeta().hasDisplayName()) return;
        if (!e.getItem().getItemMeta().hasLore()) return;
        if (user.getClaiming() == null) return;
        if (!e.getItem().isSimilar(claimWand)) return;
        e.setCancelled(true);
        Claiming.hidePillars(p);
        p.sendMessage(C.chat(localeConfig.getString("claiming.success.cancel")));
        user.setClaiming(null);
        p.getInventory().remove(claimWand);
    }

    @EventHandler
    public void onLeftClickCrouchAir(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action a = e.getAction();
        User user = User.get(p);
        ItemStack claimWand = Faction.getClaimWand();
        if (!a.equals(Action.LEFT_CLICK_AIR) || !p.isSneaking()) return;
        if (e.getItem() == null) return;
        if (!e.getItem().hasItemMeta()) return;
        if (!e.getItem().getItemMeta().hasDisplayName()) return;
        if (!e.getItem().getItemMeta().hasLore()) return;
        if (user.getClaiming() == null) return;
        if (!e.getItem().isSimilar(claimWand)) return;
        e.setCancelled(true);
        if (user.getFaction() == null) {
            user.setClaiming(null);
            p.getInventory().remove(claimWand);
            return;
        }
        Faction faction = Faction.get(user.getFaction());
        if (faction == null) {
            user.setClaiming(null);
            p.getInventory().remove(claimWand);
            return;
        }

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
        if (invalidSize(p, claim)) return;
        if (claim.intersects()) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.intersects")));
            return;
        }

        double cost = claim.price();
        if (!claiming.isClaimingFor()) {
            if (faction.getBalance() < cost) {
                p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("claiming.error.not-enough-money"))));
                return;
            }
        }

        if (!Main.getInstance().addClaim(claiming.getFaction(), one, two)) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.save")));
            return;
        }
        if (!user.getMap().contains(claim)) {
            user.getMap().add(claim);
        }
        p.sendMessage(C.chat(localeConfig.getString("claiming.success.claim")));
        if (!claiming.isClaimingFor()) {
            faction.setBalance(faction.getBalance() - cost);
        }
        user.setClaiming(null);
        p.getInventory().remove(claimWand);
    }

    private boolean invalidSize(Player p, Claim claim) {
        User user = User.get(p);
        Claiming claiming = user.getClaiming();
        if (claiming == null) return true;
        if (claiming.isClaimingFor()) return false;
        if (claim.isTooSmall()) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.too-small")));
            return true;
        }
        if (claim.isTooLarge()) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.too-large")));
            return true;
        }
        return false;
    }

}
