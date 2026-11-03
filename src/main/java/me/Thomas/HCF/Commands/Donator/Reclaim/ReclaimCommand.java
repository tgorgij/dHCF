package me.Thomas.HCF.Commands.Donator.Reclaim;

import me.Thomas.HCF.Commands.Donator.Reclaim.Subcommands.ResetCommand;
import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public class ReclaimCommand implements TabExecutor {
    private final Map<String, Subcommand> subcommands = new HashMap<>();

    public ReclaimCommand() { register(new ResetCommand()); }

    private void register(Subcommand subcommand) {
        subcommands.put(subcommand.getName(), subcommand);
        for (String alias : subcommand.getAliases()) subcommands.put(alias, subcommand);
    }

    public static void send(CommandSender sender, String key, String... replacements) {
        String message = Main.getInstance().getLocaleConfig().getString("commands.reclaim." + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) message = message.replace(replacements[i], replacements[i + 1]);
        sender.sendMessage(C.chat(message));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Main.getInstance().isFeatureEnabled("reclaim")) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.feature-disabled")));
            return true;
        }
        if (args.length > 0) {
            Subcommand subcommand = subcommands.get(args[0].toLowerCase(Locale.ROOT));
            if (subcommand != null) return subcommand.execute(sender, Arrays.copyOfRange(args, 1, args.length), alias);
            send(sender, "usage", "%alias%", alias);
            return true;
        }
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        User user = User.get(p);
        if (p.isDead() || user.isInStaffMode()) { send(p, "unavailable"); return true; }
        if (user.hasReclaimed()) { send(p, "already"); return true; }
        List<String> commands = null;
        for (Map<?, ?> entry : Main.getInstance().getConfig().getMapList("reclaim.permissions")) {
            if (!(entry.get("permission") instanceof String permission) || !p.hasPermission(permission)) continue;
            commands = new ArrayList<>();
            if (entry.get("commands") instanceof List<?> rewards) for (Object reward : rewards) {
                if (!(reward instanceof String configured)) continue;
                String rewardCommand = configured.strip();
                if (rewardCommand.startsWith("/")) rewardCommand = rewardCommand.substring(1).stripLeading();
                if (!rewardCommand.isBlank()) commands.add(rewardCommand.replace("%player%", p.getName()).replace("%uuid%", p.getUniqueId().toString()));
            }
            break;
        }
        if (commands == null) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission")));
            return true;
        }
        if (commands.isEmpty()) { send(p, "no-rewards"); return true; }
        user.setReclaimed(true);
        try { user.save(); }
        catch (RuntimeException exception) {
            user.setReclaimed(false);
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not save reclaim for " + p.getUniqueId(), exception);
            send(p, "failed");
            return true;
        }
        boolean failed = false;
        for (String reward : commands) {
            try {
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), reward)) {
                    failed = true;
                    Main.getInstance().getLogger().warning("Reclaim reward failed for " + p.getUniqueId() + ": " + reward);
                }
            } catch (RuntimeException exception) {
                failed = true;
                Main.getInstance().getLogger().log(Level.SEVERE, "Reclaim reward failed for " + p.getUniqueId() + ": " + reward, exception);
            }
        }
        send(p, failed ? "reward-failed" : "success");
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!Main.getInstance().isFeatureEnabled("reclaim")) return List.of();
        if (!Permission.has(sender, "command.reclaim.reset", "admin") || args.length == 0) return List.of();
        if (args.length == 1) return subcommands.keySet().stream().filter(name -> name.startsWith(args[0].toLowerCase(Locale.ROOT))).sorted().toList();
        Subcommand subcommand = subcommands.get(args[0].toLowerCase(Locale.ROOT));
        return subcommand == null ? List.of() : subcommand.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
    }
}
