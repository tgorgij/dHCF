package me.Thomas.HCF.Commands.Admin;

import me.Thomas.HCF.Events.Player.Item.UserSpawnerEvent;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Ability.AbilityItems;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class AbilityCommand implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.ability", "admin")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length < 2 || args.length > 3 || AbilityItems.getType(args[1]) == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("abilities.usage")));
            return true;
        }
        Player p = Bukkit.getPlayerExact(args[0]);
        if (p == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        int amount;
        try {
            amount = args.length == 3 ? Integer.parseInt(args[2]) : 1;
            if (amount < 1 || amount > 2304) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.invalid-amount")));
            return true;
        }
        if (p.isDead() || User.get(p).isInStaffMode()) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("abilities.unavailable")));
            return true;
        }
        AbilityItems.Type type = AbilityItems.getType(args[1]);
        // Give bows separately: every bow must keep a unique charge identity.
        if (type == AbilityItems.Type.ARCHER) {
            long empty = Arrays.stream(p.getInventory().getStorageContents()).filter(item -> item == null || item.isEmpty()).count();
            if (empty < amount) {
                sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("abilities.no-space")));
                return true;
            }
            for (int i = 0; i < amount; i++) p.getInventory().addItem(AbilityItems.create(type));
        } else if (!UserSpawnerEvent.give(p, AbilityItems.create(type), amount)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("abilities.no-space")));
            return true;
        }
        sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("abilities.given")
                .replace("%player%", p.getName()).replace("%item%", type.getName()).replace("%amount%", Integer.toString(amount))));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Permission.has(sender, "command.ability", "admin") || args.length == 0) return List.of();
        String input = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(input)).sorted().toList();
        if (args.length == 2) return Arrays.stream(AbilityItems.Type.values()).map(AbilityItems.Type::getId).filter(id -> id.startsWith(input)).toList();
        return List.of();
    }
}
