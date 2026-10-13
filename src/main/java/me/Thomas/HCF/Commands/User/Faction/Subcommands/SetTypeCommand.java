package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

public class SetTypeCommand implements Subcommand {

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() {
        return "settype";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.faction.settype")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.settype.usage")).replace("%alias%", alias)));
            return true;
        }
        Faction faction = Faction.get(args[0]);
        if (faction == null) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("primary.no-faction"))));
            return true;
        }
        if (faction.getType().equals(Faction.Type.PLAYER)) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("primary.only-system"))));
            return true;
        }
        String stringType = args[1].toUpperCase(Locale.ROOT);
        Faction.Type type;
        try {
            type = Faction.Type.valueOf(stringType);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.settype.invalid-type"))));
            return true;
        }
        if (type == Faction.Type.PLAYER) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.settype.invalid-type")));
            return true;
        }
        faction.setType(type);
        sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.settype.success")).replace("%faction%", faction.getName()).replace("%type%", type.toString())));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!Permission.has(sender, "command.faction.settype")) {
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
            String input = args[1].toUpperCase(Locale.ROOT);

            return Stream.of(
                            "SYSTEM",
                    "SAFEZONE",
                    "ROAD",
                    "KOTH"
                    )
                    .filter(color -> color.startsWith(input))
                    .toList();
        }

        return List.of();
    }
}
