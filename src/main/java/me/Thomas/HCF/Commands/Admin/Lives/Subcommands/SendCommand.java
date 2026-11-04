package me.Thomas.HCF.Commands.Admin.Lives.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class SendCommand implements Subcommand {
    @Override
    public String getName() { return "send"; }
    @Override
    public List<String> getAliases() { return List.of(); }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NotNull [] args, @NotNull String alias) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player")));
            return true;
        }
        if (args.length != 2) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.lives.send.usage").replace("%alias%", alias)));
            return true;
        }
        User target = User.get(args[0]);
        if (target == null) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-player")));
            return true;
        }
        if (target.getUUID().equals(p.getUniqueId())) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.lives.send.self")));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
            if (amount <= 0 || target.getLives() < 0 || (long) target.getLives() + amount > Integer.MAX_VALUE) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.invalid-amount")));
            return true;
        }
        User user = User.get(p);
        if (user.getLives() < amount) {
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.lives.send.insufficient")));
            return true;
        }
        int previous = user.getLives(), previousTarget = target.getLives();
        user.setLives(previous - amount);
        target.setLives(previousTarget + amount);
        try {
            Main.getInstance().getDatabaseManager().save(List.of(user.snapshot(), target.snapshot())).join();
        } catch (RuntimeException exception) {
            user.setLives(previous);
            target.setLives(previousTarget);
            Main.getInstance().getLogger().log(Level.SEVERE, "Could not transfer player lives", exception);
            p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.lives.send.failed")));
            return true;
        }
        p.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.lives.send.success")
                .replace("%player%", target.getName()).replace("%amount%", Integer.toString(amount))));
        Player recipient = Bukkit.getPlayer(target.getUUID());
        if (recipient != null) recipient.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.lives.send.received")
                .replace("%player%", p.getName()).replace("%amount%", Integer.toString(amount))));
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player p) || args.length != 1) return List.of();
        return Main.getInstance().getAllUsers().values().stream().filter(user -> !user.getUUID().equals(p.getUniqueId()))
                .map(User::getName).filter(name -> !name.isEmpty() && name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT)))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
