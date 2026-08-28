package me.Thomas.HCF.Commands;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public interface Subcommand {

    String getName();

    List<String> getAliases();

    boolean execute(
            @NotNull CommandSender sender,
            @NotNull String @NonNull [] args,
            @NotNull String alias
    );

    default List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String @NonNull [] args
    ) {
        return List.of();
    }
}