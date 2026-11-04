package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Claims.Claiming;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ClaimforCommand implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "claimfor";
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
        if (!Permission.has(p, "faction.claimfor", "admin")) {
            p.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 1) {
            p.sendMessage(C.chat(localeConfig.getString("commands.faction.claimfor.usage").replace("%alias%", alias)));
            return true;
        }
        User user = User.get(p);
        if (user.getClaiming() != null) {
            p.sendMessage(C.chat(localeConfig.getString("claiming.error.faction-already")));
            return true;
        }
        String name = args[0];
        Faction faction = Faction.get(name);
        if (faction == null) {
            p.sendMessage(C.chat(localeConfig.getString("primary.no-faction")));
            return true;
        }
        if (faction.getType().equals(Faction.Type.PLAYER)) {
            p.sendMessage(C.chat(localeConfig.getString("primary.only-system")));
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
        user.setClaiming(new Claiming(faction.getUUID(), true));
        Claiming.showNearbyClaims(p);
        p.sendMessage(C.chat(localeConfig.getString("commands.faction.claimfor.success").replace("%faction%", faction.getName())));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!Permission.has(sender, "command.faction.claimfor", "admin")) {
            return List.of();
        }
        if (args.length == 1) {
            String input = args[0].toUpperCase(Locale.ROOT);

            return Main.getInstance().getAllFactions().values().stream()
                    .map(Faction::getName)
                    .filter(Objects::nonNull)
                    .filter(name -> name.toUpperCase(Locale.ROOT).startsWith(input))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        return List.of();
    }
}
