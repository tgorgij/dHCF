package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.IO.Locale.LocaleConfig;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

public class SetColorCommand implements Subcommand {

    private final LocaleConfig localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "setcolor";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.faction.setcolor")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.setcolor.usage").replace("%alias%", alias)));
            return true;
        }
        Faction faction = Faction.get(args[0]);
        if (faction == null) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-faction")));
            return true;
        }
        if (faction.getType().equals(Faction.Type.PLAYER)) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.only-system")));
            return true;
        }
        String color = args[1];
        if (C.getColorCode(color).isEmpty()) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.invalid-color")));
            return true;
        }

        String code = C.getColorCode(color);
        faction.setColor(color.toLowerCase(Locale.ROOT));
        sender.sendMessage(C.chat(localeConfig.getString("commands.faction.setcolor.success").replace("%faction%", faction.getName()).replace("%color%", code)));
        return true;
    }

    @Override
    public List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String @NonNull [] args
    ) {
        if (!Permission.has(sender, "command.faction.setcolor")) {
            return List.of();
        }
        if (args.length == 1) {
            String input = args[0].toUpperCase(Locale.ROOT);

            return Main.getInstance().getAllFactions().values().stream()
                    .filter(faction -> faction.getType() != Faction.Type.PLAYER)
                    .map(Faction::getName)
                    .filter(Objects::nonNull)
                    .filter(name -> name.toUpperCase(Locale.ROOT).startsWith(input))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        if (args.length == 2) {
            String input = args[1].toLowerCase(Locale.ROOT);

            return Stream.of(
                            "black",
                            "darkblue",
                            "green",
                            "cyan",
                            "darkred",
                            "purple",
                            "gold",
                            "gray",
                            "darkgray",
                            "blue",
                            "lime",
                            "aqua",
                            "red",
                            "pink",
                            "yellow",
                            "white"
                    )
                    .filter(color -> color.startsWith(input))
                    .toList();
        }

        return List.of();
    }
}
