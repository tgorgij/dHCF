package me.Thomas.HCF.Commands.User;

import me.Thomas.HCF.Events.Player.Interact.UserRewardsEvent;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import me.Thomas.HCF.Utils.Rewards.TechTree;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public class RewardsCommand implements TabExecutor {
    private boolean admin(CommandSender sender) { return Permission.has(sender, "command.rewards.list", "admin"); }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length > 0) {
            if (!admin(sender)) { sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-permission"))); return true; }
            if (args.length != 2 || !args[1].equalsIgnoreCase("list")) {
                sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("commands.rewards.admin-usage").replace("%alias%", alias)));
                return true;
            }
            Faction faction = Faction.get(args[0]);
            if (faction == null) { sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.no-faction"))); return true; }
            if (faction.getType() != Faction.Type.PLAYER) { sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.only-player-faction"))); return true; }
            for (String line : Main.getInstance().getMessagesConfig().getStringList("rewards.list.header")) {
                sender.sendMessage(C.chat(line.replace("%faction%", faction.getName())));
            }
            TechTree tech = TechTree.get();
            for (TechTree.Tree tree : tech.getTrees()) {
                String name = Main.getInstance().getGuiConfig().getString("rewards.trees." + tree.id() + ".label", tree.id());
                sender.sendMessage(C.chat(Main.getInstance().getMessagesConfig().getString("rewards.list.tree").replace("%tree%", name)));
                List<TechTree.Node> nodes = tree.nodes().stream().filter(node -> tech.isUnlocked(faction, tree, node)).toList();
                if (nodes.isEmpty()) sender.sendMessage(C.chat(Main.getInstance().getMessagesConfig().getString("rewards.list.empty")));
                for (TechTree.Node node : nodes) sender.sendMessage(C.chat(Main.getInstance().getMessagesConfig().getString("rewards.list.entry")
                        .replace("%reward%", UserRewardsEvent.label(tree, node))));
            }
            return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage(C.chat(Main.getInstance().getLocaleConfig().getString("primary.not-player"))); return true; }
        String denied = TechTree.access(p);
        if (denied != null) { UserRewardsEvent.message(p, denied); return true; }
        UserRewardsEvent.open(p);
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                              @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!admin(sender)) return List.of();
        if (args.length == 1) return Main.getInstance().getAllFactions().values().stream().filter(faction -> faction.getType() == Faction.Type.PLAYER)
                .map(Faction::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        return args.length == 2 && "list".startsWith(args[1].toLowerCase(Locale.ROOT)) ? List.of("list") : List.of();
    }
}
