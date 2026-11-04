package me.Thomas.HCF.Events.Player.Combat;

import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public class UserAllyDamageEvent implements Listener {
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        User target = UserDamageEvent.getVictimUser(victim);
        if (target == null) return;
        Player attacker = UserDamageEvent.resolveAttacker(e.getDamageSource().getCausingEntity());
        if (attacker == null && e instanceof EntityDamageByEntityEvent hit) attacker = UserDamageEvent.resolveAttacker(hit.getDamager());
        if (attacker == null) return;
        User user = User.get(attacker);
        Faction faction = user.hasFaction() ? Faction.get(user.getFaction()) : null;
        if (faction == null || !faction.isAllied(target.getFaction())) return;
        double multiplier = Main.getInstance().getConfig().getDouble("factions.allies.damage-multiplier", 0.5);
        if (!Double.isFinite(multiplier)) multiplier = 0.5;
        multiplier = Math.clamp(multiplier, 0.0, 1.0);
        // Scale each modifier so armour, absorption, and true-damage bonuses retain the same reduction.
        for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
            if (e.isApplicable(modifier)) e.setDamage(modifier, e.getDamage(modifier) * multiplier);
        }
    }
}
