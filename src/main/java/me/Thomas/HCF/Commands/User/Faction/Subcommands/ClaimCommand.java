package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Claims.Claiming;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Objects;

public class ClaimCommand implements Subcommand {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "claim";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
            return true;
        }
        if (args.length != 0) {
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.claim.usage")).replace("%alias%", alias)));
            return true;
        }
        User user = User.get(p);
        if (user.getClaiming() != null) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.already")));
            return true;
        }
        if (!user.hasFaction()) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.not-in")));
            return true;
        }
        Faction faction = Faction.get(user.getFaction());
        if (faction.hasClaim()) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.has")));
            return true;
        }
        if (faction.getMemberType(p.getUniqueId()).equals(Faction.MemberType.MEMBER) || faction.getMemberType(p.getUniqueId()).equals(Faction.MemberType.CAPTAIN)) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.coleader.not")));
            return true;
        }
        if (faction.isClaiming()) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.already")));
            return true;
        }
        final Inventory inventory = p.getInventory();
        final int emptySlot = p.getInventory().firstEmpty();
        final ItemStack item = inventory.getItem(0);

        if (emptySlot == -1) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.claim-full")));
            return true;
        }
        inventory.setItem(emptySlot, item);
        inventory.setItem(0, Faction.getClaimWand());
        user.setClaiming(new Claiming(faction.getUUID(), false));
        p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.claim.success"))));
        return true;
    }
}
