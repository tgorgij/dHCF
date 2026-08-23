package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public interface FactionSubcommand {

    String getName();

    List<String> getAliases();

    boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias);
}