package me.Thomas.HCF.Events.Player.Item;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.IO.Claims.Claim;
import me.Thomas.HCF.IO.Claims.Claiming;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;


public class UserClaimEvent implements Listener {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @EventHandler
    public void onLeftClickBlock(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action a = e.getAction();
        User user = User.get(p);
        if (!a.equals(Action.LEFT_CLICK_BLOCK)) return;
        if (e.getItem() == null) return;
        if (!e.getItem().hasItemMeta()) return;
        if (user.getClaiming() == null) return;
        ItemStack claimWand = Faction.getClaimWand();
        if (!e.getItem().isSimilar(claimWand)) return;
        e.setCancelled(true);
        if (validateClaiming(p, claimWand) == null) return;
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
        if (invalidClaim(p, claim)) return;
        if (claim.intersects()) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.intersects")));
            return;
        }

        Claiming.resetSelectionPillars(claiming, p);
        claiming.setOne(e.getClickedBlock().getLocation());
        Claiming.generateClaimPillars(claim, claiming.getPillar(), p);
        p.sendMessage(C.chat(localeConfig.getString("claiming.success.one")));
        p.sendMessage(C.chat(localeConfig.getString("claiming.purchase.cost").replace("%cost%", C.getFormattedMoney(claim.price())).replace("%size-x%", claim.width() + "").replace("%size-z%", claim.length() + "")));

    }

    @EventHandler
    public void onRightClickBlock(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action a = e.getAction();
        User user = User.get(p);
        if (!a.equals(Action.RIGHT_CLICK_BLOCK)) return;
        if (e.getItem() == null) return;
        if (!e.getItem().hasItemMeta()) return;
        if (user.getClaiming() == null) return;
        ItemStack claimWand = Faction.getClaimWand();
        if (!e.getItem().isSimilar(claimWand)) return;
        e.setCancelled(true);
        if (validateClaiming(p, claimWand) == null) return;

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
        if (invalidClaim(p, claim)) return;
        if (claim.intersects()) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.intersects")));
            return;
        }

        Claiming.resetSelectionPillars(claiming, p);
        claiming.setTwo(e.getClickedBlock().getLocation());
        Claiming.generateClaimPillars(claim, claiming.getPillar(), p);
        p.sendMessage(C.chat(localeConfig.getString("claiming.success.two")));

        p.sendMessage(C.chat(localeConfig.getString("claiming.purchase.cost").replace("%cost%", C.getFormattedMoney(claim.price())).replace("%size-x%", claim.width() + "").replace("%size-z%", claim.length() + "")));
    }

    @EventHandler
    public void onRightClickAir(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Action a = e.getAction();
        User user = User.get(p);
        if (!a.equals(Action.RIGHT_CLICK_AIR) || !p.isSneaking()) return;
        if (e.getItem() == null) return;
        if (!e.getItem().hasItemMeta()) return;
        if (user.getClaiming() == null) return;
        ItemStack claimWand = Faction.getClaimWand();
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
        if (!a.equals(Action.LEFT_CLICK_AIR) || !p.isSneaking()) return;
        if (e.getItem() == null) return;
        if (!e.getItem().hasItemMeta()) return;
        if (user.getClaiming() == null) return;
        ItemStack claimWand = Faction.getClaimWand();
        if (!e.getItem().isSimilar(claimWand)) return;
        e.setCancelled(true);
        Faction faction = validateClaiming(p, claimWand);
        if (faction == null) return;

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
        if (invalidClaim(p, claim)) return;
        if (claim.intersects()) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.intersects")));
            return;
        }

        double cost = claim.price();
        if (!claiming.isClaimingFor()) {
            if (faction.getBalance() < cost) {
                p.sendMessage(C.chat(localeConfig.getString("claiming.error.not-enough-money")));
                return;
            }
        }

        if (!Main.getInstance().addClaim(claiming.getFaction(), one, two, claiming.isClaimingFor() ? 0 : cost)) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.save")));
            return;
        }
        if (!user.getMap().contains(claim)) {
            user.getMap().add(claim);
        }
        p.sendMessage(C.chat(localeConfig.getString("claiming.success.claim")));
        user.setClaiming(null);
        p.getInventory().remove(claimWand);
    }

    private Faction validateClaiming(Player p, ItemStack claimWand) {
        User user = User.get(p);
        Claiming claiming = user.getClaiming();
        Faction faction = Faction.get(claiming.getFaction());
        String message = null;
        if (faction == null) message = "primary.no-faction";
        else if (claiming.isClaimingFor()) {
            if (!Permission.has(p, "faction.claimfor", "admin")) message = "primary.no-permission";
            else if (faction.getType() == Faction.Type.PLAYER) message = "primary.only-system";
        } else if (faction.getType() != Faction.Type.PLAYER || !faction.getUUID().equals(user.getFaction())
                || !faction.hasMember(user.getUUID())) {
            message = "commands.faction.not-in";
        } else if (faction.getMemberType(user.getUUID()) != Faction.MemberType.LEADER
                && faction.getMemberType(user.getUUID()) != Faction.MemberType.COLEADER) {
            message = "commands.faction.coleader.not";
        }
        if (message == null) return faction;
        Claiming.hidePillars(p);
        user.setClaiming(null);
        p.getInventory().remove(claimWand);
        p.sendMessage(C.chat(localeConfig.getString(message)));
        return null;
    }

    private boolean invalidClaim(Player p, Claim claim) {
        User user = User.get(p);
        Claiming claiming = user.getClaiming();
        if (claiming == null) return true;
        if (claiming.isClaimingFor()) return false;
        // The rectangle overlaps warzone if its closest point to the origin lies in warzone.
        Location closest = claim.one();
        closest.setX(Math.clamp(0, claim.minX(), claim.maxX()));
        closest.setZ(Math.clamp(0, claim.minZ(), claim.maxZ()));
        if (!Faction.isInWilderness(closest)) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.not-in-wilderness")));
            return true;
        }
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
