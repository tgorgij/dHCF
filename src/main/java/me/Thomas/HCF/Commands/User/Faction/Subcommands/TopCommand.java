package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import java.util.*;

public class TopCommand implements Subcommand {
    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();
    @Override public String getName() { return "top"; }
    @Override public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        int page = 1;
        try {
            if (args.length > 1) throw new NumberFormatException();
            if (args.length == 1) page = Integer.parseInt(args[0]);
            if (page < 1) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.top.usage")).replace("%alias%", alias)));
            return true;
        }
        // Capture points once so sorting and displayed rankings use the same snapshot.
        record Entry(Faction faction, int points) {}
        List<Entry> entries = Main.getInstance().getAllFactions().values().stream()
                .filter(faction -> faction.getType() == Faction.Type.PLAYER)
                .map(faction -> new Entry(faction, faction.getPoints()))
                .sorted(Comparator.comparingInt(Entry::points).reversed()
                        .thenComparing(entry -> entry.faction().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(entry -> entry.faction().getUUID()))
                .toList();
        int pages = Math.max(1, (entries.size() + 9) / 10);
        if (page > pages) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.top.invalid-page"))
                    .replace("%pages%", Integer.toString(pages))));
            return true;
        }
        User viewer = sender instanceof Player player ? User.get(player) : null;
        FileConfiguration messages = Main.getInstance().getMessagesConfig();
        String pageText = Integer.toString(page), pagesText = Integer.toString(pages);
        for (String line : messages.getStringList("factions.top.header")) {
            sender.sendMessage(C.chat(line.replace("%page%", pageText).replace("%pages%", pagesText)));
        }
        int start = (page - 1) * 10;
        for (int index = start; index < Math.min(start + 10, entries.size()); index++) {
            Entry entry = entries.get(index);
            Faction faction = entry.faction();
            boolean team = viewer != null && Objects.equals(viewer.getFaction(), faction.getUUID());
            String color = Main.getInstance().getConfig().getString(team ? "factions.colors.team" : "factions.colors.enemy");
            String line = Objects.requireNonNull(messages.getString("factions.top.entry"))
                    .replace("%number%", Integer.toString(index + 1))
                    .replace("%color%", C.getColorCode(Objects.requireNonNull(color)))
                    .replace("%faction%", faction.getName())
                    .replace("%points%", Integer.toString(entry.points()))
                    .replace("%max%", Integer.toString(faction.getMembers().size()));
            sender.sendMessage(C.chat(line));
        }
        for (String line : messages.getStringList("factions.top.footer")) {
            sender.sendMessage(C.chat(line.replace("%page%", pageText).replace("%pages%", pagesText)));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (args.length != 1) return List.of();
        long factions = Main.getInstance().getAllFactions().values().stream()
                .filter(faction -> faction.getType() == Faction.Type.PLAYER).count();
        int pages = (int) Math.max(1, (factions + 9) / 10);
        return java.util.stream.IntStream.rangeClosed(1, pages).mapToObj(Integer::toString)
                .filter(page -> page.startsWith(args[0])).toList();
    }
}
