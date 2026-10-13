package me.Thomas.HCF.Commands.Admin.SOTW.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class EnableCommand implements Subcommand {
    @Override
    public String getName() {
        return "enable";
    }

    @Override
    public List<String> getAliases() {
        return List.of();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        return false;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        return Subcommand.super.tabComplete(sender, args);
    }
}
