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

public final class ListCommand implements Subcommand {
    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();
    @Override public String getName() { return "list"; }
    @Override public List<String> getAliases() { return List.of(); }

    public static int firstNumber(int page) {
        return page == 1 ? 1 : (page - 1) * 10;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        int page = 1;
        try {
            if (args.length > 1) throw new NumberFormatException();
            if (args.length == 1) page = Integer.parseInt(args[0]);
            if (page < 1) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.list.usage")).replace("%alias%", alias)));
            return true;
        }
        // Capture online counts once so sorting and the displayed values use the same snapshot.
        record Entry(Faction faction, int online) {}
        List<Entry> entries = Main.getInstance().getAllFactions().values().stream()
                .filter(faction -> faction.getType() == Faction.Type.PLAYER)
                .map(faction -> new Entry(faction, faction.getOnlineMembers().size()))
                .sorted(Comparator.comparingInt(Entry::online).reversed()
                        .thenComparing(entry -> entry.faction().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(entry -> entry.faction().getUUID()))
                .toList();
        int pages = Math.max(1, (entries.size() + 9) / 10);
        if (page > pages) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.list.invalid-page"))
                    .replace("%pages%", Integer.toString(pages))));
            return true;
        }
        User viewer = sender instanceof Player player ? User.get(player) : null;
        FileConfiguration messages = Main.getInstance().getMessagesConfig();
        String pageText = Integer.toString(page), pagesText = Integer.toString(pages);
        for (String line : messages.getStringList("factions.list.header")) {
            sender.sendMessage(C.chat(line.replace("%page%", pageText).replace("%pages%", pagesText)));
        }
        int start = (page - 1) * 10;
        for (int index = start; index < Math.min(start + 10, entries.size()); index++) {
            Entry entry = entries.get(index);
            Faction faction = entry.faction();
            boolean team = viewer != null && Objects.equals(viewer.getFaction(), faction.getUUID());
            String color = Main.getInstance().getConfig().getString(team ? "factions.colors.team" : "factions.colors.enemy");
            String line = Objects.requireNonNull(messages.getString("factions.list.entry"))
                    .replace("%number%", Integer.toString(firstNumber(page) + index - start))
                    .replace("%color%", C.getColorCode(Objects.requireNonNull(color)))
                    .replace("%faction%", faction.getName())
                    .replace("%online%", Integer.toString(entry.online()))
                    .replace("%max%", Integer.toString(faction.getMembers().size()));
            sender.sendMessage(C.chat(line));
        }
        for (String line : messages.getStringList("factions.list.footer")) {
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
