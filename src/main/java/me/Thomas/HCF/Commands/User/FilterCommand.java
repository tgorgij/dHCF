package me.Thomas.HCF.Commands.User;

import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class FilterCommand implements TabExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                             @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        if (args.length > 1) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.filter.usage").replace("%alias%", alias)));
            return true;
        }
        User user = User.get(p);
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            if (user.getFilteredItems().isEmpty()) p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.filter.empty")));
            else p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.filter.list")
                    .replace("%items%", user.getFilteredItems().stream().map(material -> material.name().toLowerCase(Locale.ROOT))
                            .sorted().collect(Collectors.joining(", ")))));
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.filter.usage").replace("%alias%", alias)));
            return true;
        }
        if (args[0].equalsIgnoreCase("clear")) {
            user.clearFilteredItems();
            user.saveAsync();
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.filter.cleared")));
            return true;
        }
        Material material = Material.matchMaterial(args[0]);
        if (material == null || material.isLegacy() || material.isAir() || !material.isItem()) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.filter.invalid-item")));
            return true;
        }
        boolean filtered = user.toggleFilteredItem(material);
        user.saveAsync();
        p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.filter." + (filtered ? "added" : "removed"))
                .replace("%item%", material.name().toLowerCase(Locale.ROOT))));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player) || args.length != 1) return List.of();
        String input = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>(List.of("list", "clear"));
        names.addAll(Arrays.stream(Material.values()).filter(material -> !material.isLegacy() && !material.isAir())
                .filter(material -> material.name().toLowerCase(Locale.ROOT).startsWith(input) && material.isItem())
                .map(material -> material.name().toLowerCase(Locale.ROOT)).toList());
        return names.stream().filter(name -> name.startsWith(input)).sorted().toList();
    }
}
