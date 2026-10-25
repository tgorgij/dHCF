package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Combat.DTR;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SetRegenCommand implements Subcommand {
    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public String getName() { return "setregen"; }

    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (!Permission.has(sender, "command.faction." + getName(), "admin")) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-permission")));
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction." + getName() + ".usage"))
                    .replace("%alias%", alias)));
            return true;
        }
        Faction faction = Faction.get(args[0]);
        if (faction == null) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.no-faction")));
            return true;
        }
        if (faction.getType() != Faction.Type.PLAYER) {
            sender.sendMessage(C.chat(localeConfig.getString("primary.only-player-faction")));
            return true;
        }
        if (!args[1].equalsIgnoreCase("true") && !args[1].equalsIgnoreCase("false")) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.setregen.invalid-state")));
            return true;
        }
        boolean regenerating = Boolean.parseBoolean(args[1]);
        DTR.setRegenerating(faction, regenerating, Main.getInstance().getConfig(), System.currentTimeMillis());
        faction.saveAsync();
        sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.setregen."
                + (regenerating ? "started" : "reset"))).replace("%faction%", faction.getName())));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (!Permission.has(sender, "command.faction." + getName(), "admin")) return List.of();
        if (args.length == 2) {
            String input = args[1].toLowerCase(Locale.ROOT);
            return List.of("true", "false").stream().filter(value -> value.startsWith(input)).toList();
        }
        if (args.length != 1) return List.of();
        String input = args[0].toUpperCase(Locale.ROOT);
        return Main.getInstance().getAllFactions().values().stream()
                .filter(faction -> faction.getType() == Faction.Type.PLAYER)
                .map(Faction::getName).filter(Objects::nonNull)
                .filter(name -> name.toUpperCase(Locale.ROOT).startsWith(input))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
