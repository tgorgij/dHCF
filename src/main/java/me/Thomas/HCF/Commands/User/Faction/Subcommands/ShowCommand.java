package me.Thomas.HCF.Commands.User.Faction.Subcommands;

import me.Thomas.HCF.Commands.Subcommand;
import me.Thomas.HCF.IO.Faction.Faction;
import me.Thomas.HCF.IO.User.User;
import me.Thomas.HCF.Main.Main;
import me.Thomas.HCF.Utils.Chat.C;
import me.Thomas.HCF.Utils.Permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.*;

public class ShowCommand implements Subcommand {

    @Override
    public String getName() {
        return "show";
    }

    @Override
    public List<String> getAliases() {
        return List.of("who");
    }

    private final FileConfiguration localeConfig = Main.getInstance().getLocaleConfig();

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String @NonNull [] args, @NotNull String alias) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(C.chat(localeConfig.getString("primary.not-player")));
                return true;
            }
            User user = User.get(p);
            if (!user.hasFaction()) {
                p.sendMessage(C.chat(localeConfig.getString("commands.faction.not-in")));
                return true;
            }
            showPlayerFaction(p, Faction.get(user.getFaction()));
            if (Main.getInstance().isFactionDebug()) {
                p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("faction.debug.type")).replace("%type%", Faction.get(user.getFaction()).getType().name().toLowerCase(Locale.ROOT))));
            }
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.show.usage")).replace("%alias%", alias)));
            return true;
        }
        String name = args[0];
        Faction faction = Faction.get(name);
        if (faction != null) {
            if (!faction.getType().equals(Faction.Type.PLAYER)) {
                showSystemFaction(sender, faction);
                if (Main.getInstance().isFactionDebug()) {
                    sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("faction.debug.type")).replace("%type%", faction.getType().name().toLowerCase(Locale.ROOT))));
                }
                return true;
            }
            if (!(sender instanceof Player p)) {
                showPlayerFactionAsConsole(sender, faction);
                if (Main.getInstance().isFactionDebug()) {
                    sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("faction.debug.type")).replace("%type%", faction.getType().name().toLowerCase(Locale.ROOT))));
                }
                return true;
            }
            showPlayerFaction(p, faction);
            if (Main.getInstance().isFactionDebug()) {
                p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("faction.debug.type")).replace("%type%", faction.getType().name().toLowerCase(Locale.ROOT))));
            }
            return true;
        }
        User user = User.get(name);
        if (user == null) {
            sender.sendMessage(C.chat(localeConfig.getString("commands.faction.show.none")));
            return true;
        }
        if (!user.hasFaction()) {
            sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("commands.faction.show.no-faction")).replace("%player%", user.getName())));
            return true;
        }
        if (!(sender instanceof Player p)) {
            showPlayerFactionAsConsole(sender, Faction.get(user.getFaction()));
            if (Main.getInstance().isFactionDebug()) {
                sender.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("faction.debug.type")).replace("%type%", Faction.get(user.getFaction()).getType().name().toLowerCase(Locale.ROOT))));
            }
            return true;
        }
        showPlayerFaction(p, Faction.get(user.getFaction()));
        if (Main.getInstance().isFactionDebug()) {
            p.sendMessage(C.chat(Objects.requireNonNull(localeConfig.getString("faction.debug.type")).replace("%type%", Faction.get(user.getFaction()).getType().name().toLowerCase(Locale.ROOT))));
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String @NonNull [] args) {
        if (args.length == 1) {
            String input = args[0].toUpperCase(Locale.ROOT);

            return Main.getInstance().getAllFactions().values().stream()
                    .map(Faction::getName)
                    .filter(Objects::nonNull)
                    .filter(name -> name.toUpperCase(Locale.ROOT).startsWith(input))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
        return List.of();
    }

    private void showSystemFaction(CommandSender sender, Faction faction) {
        for (String s : Main.getInstance().getMessagesConfig().getStringList("factions.show.system")) {
            if (s.contains("%faction%")) {
                s = s.replace("%faction%", C.getColorCode(faction.getColor()) + faction.getName());
            }
            if (s.contains("%hq%")) {
                Location home = faction.getHome();
                if (home != null) {
                    s = s.replace("%hq%", Objects.requireNonNull(Main.getInstance().getConfig().getString("settings.location-format")).replace("%x%", home.getBlockX() + "").replace("%z%", home.getBlockZ() + ""));
                } else {
                    s = s.replace("%hq%", Objects.requireNonNull(localeConfig.getString("primary.none")));
                }
            }
            sender.sendMessage(C.chat(s));
        }
    }

    private void showPlayerFaction(Player p, Faction faction) {
        User user = User.get(p);
        for (String s : Main.getInstance().getMessagesConfig().getStringList("factions.show.player")) {
            if (s.contains("%faction%")) {
                String color = Main.getInstance().getConfig().getString("factions.colors.enemy");
                if (user.hasFaction() && user.getFaction().equals(faction.getUUID())) {
                    color = Main.getInstance().getConfig().getString("factions.colors.team");
                }
                s = s.replace("%faction%", C.getColorCode(Objects.requireNonNull(color)) + faction.getName());
            }
            if (s.contains("%online-members%")) {
                s = s.replace("%online-members%", faction.getOnlineMembers().size() + "");
            }
            if (s.contains("%total-members%")) {
                s = s.replace("%total-members%", faction.getMembers().size() + "");
            }
            if (s.contains("%hq%")) {
                Location home = faction.getHome();
                if (home != null) {
                    s = s.replace("%hq%", Objects.requireNonNull(Main.getInstance().getConfig().getString("settings.location-format")).replace("%x%", home.getBlockX() + "").replace("%z%", home.getBlockZ() + ""));
                } else {
                    s = s.replace("%hq%", Objects.requireNonNull(localeConfig.getString("primary.none")));
                }
            }
            if (s.contains("%leader%")) {
                s = s.replace("%leader%", getName(faction.getLeader()));
            }

            if (s.contains("%coleaders%")) {
                ArrayList<String> people = new ArrayList<>();
                for (Map.Entry<UUID, Integer> entry : faction.getMembers().entrySet()) {
                    UUID uuid = entry.getKey();
                    Faction.MemberType memberType = Faction.MemberType.getMemberType(entry.getValue());
                    if (memberType == null) continue;
                    if (!memberType.equals(Faction.MemberType.COLEADER)) continue;
                    people.add(getName(uuid));
                }
                if (people.isEmpty()) continue;
                s = s.replace("%coleaders%", String.join("&e, ", people));
            }

            if (s.contains("%captains%")) {
                ArrayList<String> people = new ArrayList<>();
                for (Map.Entry<UUID, Integer> entry : faction.getMembers().entrySet()) {
                    UUID uuid = entry.getKey();
                    Faction.MemberType memberType = Faction.MemberType.getMemberType(entry.getValue());
                    if (memberType == null) continue;
                    if (!memberType.equals(Faction.MemberType.CAPTAIN)) continue;
                    people.add(getName(uuid));
                }
                if (people.isEmpty()) continue;
                s = s.replace("%captains%", String.join("&e, ", people));
            }

            if (s.contains("%members%")) {
                ArrayList<String> people = new ArrayList<>();
                for (Map.Entry<UUID, Integer> entry : faction.getMembers().entrySet()) {
                    UUID uuid = entry.getKey();
                    Faction.MemberType memberType = Faction.MemberType.getMemberType(entry.getValue());
                    if (memberType == null) continue;
                    if (!memberType.equals(Faction.MemberType.MEMBER)) continue;
                    people.add(getName(uuid));
                }
                if (people.isEmpty()) continue;
                s = s.replace("%members%", String.join("&e, ", people));
            }

            if (s.contains("%balance%")) {
                s = s.replace("%balance%", C.getFormattedMoney(faction.getBalance()));
            }

            if (s.contains("%points%")) {
                s = s.replace("%points%", faction.getPoints() + "");
            }

            s = s.replace("%koth-captures%", Integer.toString(faction.getEventCaptures()));
            if (s.contains("%event-captures%")) {
                s = s.replace("%event-captures%", faction.getEventCaptures() + "");
            }

            if (s.contains("%dtr%")) {
                s = s.replace("%dtr%", C.getFormattedDTR(faction));
            }

            if (s.contains("%open%")) {
                if (!faction.isOpen()) continue;
                s = s.replace("%open%", Objects.requireNonNull(localeConfig.getString("primary.status.open")));
            }

            if (s.contains("%lives%")) {
                if (!Objects.equals(user.getFaction(), faction.getUUID()) && !Permission.has(p, "staff")) continue;
                s = s.replace("%lives%", faction.getLives() + "");
            }

            if (s.contains("%regeneration-time%")) {
                if (!Objects.equals(user.getFaction(), faction.getUUID()) && !Permission.has(p, "staff")) continue;
                String time = getRegenerationTime(faction);
                if (time == null) continue;
                s = s.replace("%regeneration-time%", time);
            }

            if (s.contains("%announcement%")) {
                if (!Objects.equals(user.getFaction(), faction.getUUID()) && !Permission.has(p, "staff")) continue;
                s = s.replace("%announcement%", faction.getAnnouncement() == null ? Objects.requireNonNull(localeConfig.getString("primary.none")) : faction.getAnnouncement());
            }
            p.sendMessage(C.chat(s));
        }
    }

    private String getName(UUID uuid) {
        User user = User.get(uuid);

        Player onlineLeader = Bukkit.getPlayer(uuid);
        String color = Main.getInstance().getConfig().getString("settings.colors.offline");

        if (onlineLeader != null) {
            color = Main.getInstance().getConfig().getString("settings.colors.online");
        } else if (user.getDeathbanExpiry() != 0) {
            color = Main.getInstance().getConfig().getString("settings.colors.deathban");
        }

        String name = C.getColorCode(Objects.requireNonNull(color)) + user.getName();
        return Objects.requireNonNull(Main.getInstance().getConfig().getString("factions.player-format")).replace("%player%", name).replace("%kills%", user.getKills() + "");
    }

    private void showPlayerFactionAsConsole(CommandSender sender, Faction faction) {
        for (String s : Main.getInstance().getMessagesConfig().getStringList("factions.show.player")) {
            if (s.contains("%faction%")) {
                String color = Main.getInstance().getConfig().getString("factions.colors.enemy");
                s = s.replace("%faction%", C.getColorCode(Objects.requireNonNull(color)) + faction.getName());
            }
            if (s.contains("%online-members%")) {
                s = s.replace("%online-members%", faction.getOnlineMembers().size() + "");
            }
            if (s.contains("%total-members%")) {
                s = s.replace("%total-members%", faction.getMembers().size() + "");
            }
            if (s.contains("%hq%")) {
                Location home = faction.getHome();
                if (home != null) {
                    s = s.replace("%hq%", Objects.requireNonNull(Main.getInstance().getConfig().getString("settings.location-format")).replace("%x%", home.getBlockX() + "").replace("%z%", home.getBlockZ() + ""));
                } else {
                    s = s.replace("%hq%", Objects.requireNonNull(localeConfig.getString("primary.none")));
                }
            }
            if (s.contains("%leader%")) {
                s = s.replace("%leader%", getName(faction.getLeader()));
            }

            if (s.contains("%coleaders%")) {
                ArrayList<String> people = new ArrayList<>();
                for (Map.Entry<UUID, Integer> entry : faction.getMembers().entrySet()) {
                    UUID uuid = entry.getKey();
                    Faction.MemberType memberType = Faction.MemberType.getMemberType(entry.getValue());
                    if (memberType == null) continue;
                    if (!memberType.equals(Faction.MemberType.COLEADER)) continue;
                    people.add(getName(uuid));
                }
                if (people.isEmpty()) continue;
                s = s.replace("%coleaders%", String.join("&e, ", people));
            }

            if (s.contains("%captains%")) {
                ArrayList<String> people = new ArrayList<>();
                for (Map.Entry<UUID, Integer> entry : faction.getMembers().entrySet()) {
                    UUID uuid = entry.getKey();
                    Faction.MemberType memberType = Faction.MemberType.getMemberType(entry.getValue());
                    if (memberType == null) continue;
                    if (!memberType.equals(Faction.MemberType.CAPTAIN)) continue;
                    people.add(getName(uuid));
                }
                if (people.isEmpty()) continue;
                s = s.replace("%captains%", String.join("&e, ", people));
            }

            if (s.contains("%members%")) {
                ArrayList<String> people = new ArrayList<>();
                for (Map.Entry<UUID, Integer> entry : faction.getMembers().entrySet()) {
                    UUID uuid = entry.getKey();
                    Faction.MemberType memberType = Faction.MemberType.getMemberType(entry.getValue());
                    if (memberType == null) continue;
                    if (!memberType.equals(Faction.MemberType.MEMBER)) continue;
                    people.add(getName(uuid));
                }
                if (people.isEmpty()) continue;
                s = s.replace("%members%", String.join("&e, ", people));
            }

            if (s.contains("%balance%")) {
                s = s.replace("%balance%", C.getFormattedMoney(faction.getBalance()));
            }

            if (s.contains("%points%")) {
                s = s.replace("%points%", faction.getPoints() + "");
            }

            s = s.replace("%koth-captures%", Integer.toString(faction.getEventCaptures()));
            if (s.contains("%event-captures%")) {
                s = s.replace("%event-captures%", faction.getEventCaptures() + "");
            }

            if (s.contains("%dtr%")) {
                s = s.replace("%dtr%", C.getFormattedDTR(faction));
            }

            if (s.contains("%open%")) {
                if (!faction.isOpen()) continue;
                s = s.replace("%open%", Objects.requireNonNull(localeConfig.getString("primary.status.open")));
            }

            if (s.contains("%lives%")) {
                if (!Permission.has(sender, "staff")) continue;
                s = s.replace("%lives%", faction.getLives() + "");
            }

            if (s.contains("%regeneration-time%")) {
                if (!Permission.has(sender, "staff")) continue;
                String time = getRegenerationTime(faction);
                if (time == null) continue;
                s = s.replace("%regeneration-time%", time);
            }

            if (s.contains("%announcement%")) {
                if (!Permission.has(sender, "staff")) continue;
                s = s.replace("%announcement%", faction.getAnnouncement() == null ? Objects.requireNonNull(localeConfig.getString("primary.none")) : faction.getAnnouncement());
            }
            sender.sendMessage(C.chat(s));
        }
    }

    private String getRegenerationTime(Faction faction) {
        long now = System.currentTimeMillis();
        if (faction.getDTRFreezeExpiry() <= now) return null;
        if (faction.getDTR() >= faction.getMaxDTR()) return null;
        double rate = Main.getInstance().getConfig().getDouble("factions.dtr.regeneration-per-minute", 0.03);
        if (!Double.isFinite(rate) || rate <= 0) return null;
        long nextRegen = faction.getDTRNextRegen();
        // A faction awaiting its first regeneration tick has a one-minute interval.
        long remaining = nextRegen == 0L ? 60_000L : Math.max(0L, nextRegen - now);
        long seconds = remaining / 1000L + (remaining % 1000L == 0L ? 0L : 1L);
        return C.formatChatTime(seconds);
    }
}
