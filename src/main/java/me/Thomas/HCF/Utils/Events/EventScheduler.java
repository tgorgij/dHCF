package me.Thomas.HCF.Utils.Events;

import me.Thomas.HCF.IO.Conquest.Conquest;
import me.Thomas.HCF.IO.DTC.DTC;
import me.Thomas.HCF.IO.Database.SaveSnapshot;
import me.Thomas.HCF.IO.KOTH.KOTH;
import me.Thomas.HCF.Main.Main;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class EventScheduler {

    public enum Type { KOTH, CONQUEST, DTC }
    public record Schedule(long id, Type type, UUID koth, LocalTime time, int seconds, LocalDate lastRun) {}

    public record Upcoming(Schedule schedule, Instant start) {}

    private final Map<Long, Schedule> schedules = new HashMap<>();
    private final ZoneId timezone;
    private final BukkitTask task;
    private long nextId = 1;

    public EventScheduler() {
        ZoneId configured;
        try {
            configured = ZoneId.of(Main.getInstance().getConfig().getString("settings.timezone", "America/Los_Angeles"), ZoneId.SHORT_IDS);
        } catch (DateTimeException exception) {
            Main.getInstance().getLogger().warning("Invalid server timezone; using America/Los_Angeles.");
            configured = ZoneId.of("America/Los_Angeles");
        }
        timezone = configured;
        try (Connection connection = Main.getInstance().getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM eventSchedules");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                String koth = rows.getString("koth"), lastRun = rows.getString("lastRun");
                Schedule schedule = new Schedule(rows.getLong("id"), Type.valueOf(rows.getString("type")),
                        koth == null ? null : UUID.fromString(koth), LocalTime.parse(rows.getString("startTime")),
                        rows.getInt("durationSeconds"), lastRun == null ? null : LocalDate.parse(lastRun));
                schedules.put(schedule.id(), schedule);
                nextId = Math.max(nextId, schedule.id() + 1);
            }
        } catch (SQLException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not load event schedules", exception);
        }
        task = Bukkit.getScheduler().runTaskTimer(Main.getInstance(), () -> check(Instant.now()), 1L, 200L);
    }

    public ZoneId getTimezone() { return timezone; }

    public List<Schedule> getSchedules() {
        return schedules.values().stream().sorted(Comparator.comparing(Schedule::time).thenComparingLong(Schedule::id)).toList();
    }

    public Upcoming getNext(Instant instant) {
        ZonedDateTime now = instant.atZone(timezone);
        Upcoming next = null;
        for (Schedule schedule : schedules.values()) {
            if (!Main.getInstance().isFeatureEnabled(schedule.type().name().toLowerCase(java.util.Locale.ROOT))) continue;
            if (schedule.type() == Type.KOTH) {
                KOTH koth = KOTH.get(schedule.koth());
                if (koth == null) continue;
            } else if (schedule.type() == Type.CONQUEST) {
                if (Conquest.get() == null) continue;
            } else if (DTC.get() == null) continue;
            LocalDate date = now.toLocalDate();
            if (schedule.lastRun() != null && !date.isAfter(schedule.lastRun())) date = schedule.lastRun().plusDays(1);
            Instant start = null;
            // Match the scheduler's local clock: skip missing DST minutes and consider both repeated minutes.
            for (int day = 0; day < 3 && start == null; day++, date = date.plusDays(1)) {
                java.time.LocalDateTime local = date.atTime(schedule.time());
                for (java.time.ZoneOffset offset : timezone.getRules().getValidOffsets(local)) {
                    Instant candidate = local.toInstant(offset);
                    if (candidate.isBefore(instant) && candidate.plusSeconds(60).isAfter(instant)) candidate = instant;
                    if (!candidate.isBefore(instant) && (start == null || candidate.isBefore(start))) start = candidate;
                }
            }
            if (start == null) continue;
            if (next == null || start.isBefore(next.start()) || start.equals(next.start()) && schedule.id() < next.schedule().id()) {
                next = new Upcoming(schedule, start);
            }
        }
        return next;
    }

    public Schedule add(Type type, UUID koth, LocalTime time, int seconds) {
        if (time.getSecond() != 0 || time.getNano() != 0 || type == null
                || type == Type.KOTH && (koth == null || KOTH.get(koth) == null || seconds <= 0)
                || type != Type.KOTH && (koth != null || seconds != 0)) throw new IllegalArgumentException("invalid");
        if (schedules.values().stream().anyMatch(schedule -> schedule.type() == type
                && java.util.Objects.equals(schedule.koth(), koth) && schedule.time().equals(time))) {
            throw new IllegalArgumentException("duplicate");
        }
        Schedule schedule = new Schedule(nextId, type, koth, time, seconds, null);
        Main.getInstance().getDatabaseManager().save(List.of(new SaveSnapshot(
                "INSERT INTO eventSchedules (id, type, koth, startTime, durationSeconds) VALUES (?, ?, ?, ?, ?)", () -> {},
                schedule.id(), type.name(), koth == null ? null : koth.toString(), time.toString(), seconds))).join();
        schedules.put(schedule.id(), schedule);
        nextId++;
        return schedule;
    }

    public boolean remove(long id) {
        if (!schedules.containsKey(id)) return false;
        Main.getInstance().getDatabaseManager().save(List.of(new SaveSnapshot(
                "DELETE FROM eventSchedules WHERE id = ?", () -> {}, id))).join();
        schedules.remove(id);
        return true;
    }

    public void removeKOTH(UUID koth) {
        schedules.values().removeIf(schedule -> koth.equals(schedule.koth()));
    }

    void check(Instant instant) {
        ZonedDateTime now = instant.atZone(timezone);
        LocalDate date = now.toLocalDate();
        LocalTime minute = now.toLocalTime().withSecond(0).withNano(0);
        for (Schedule schedule : new ArrayList<>(schedules.values())) {
            if (!Main.getInstance().isFeatureEnabled(schedule.type().name().toLowerCase(java.util.Locale.ROOT))) continue;
            if (!schedule.time().equals(minute) || schedule.lastRun() != null && !date.isAfter(schedule.lastRun())) continue;
            try {
                // Record the occurrence before starting so a restart cannot run it twice.
                Main.getInstance().getDatabaseManager().save(List.of(new SaveSnapshot(
                        "UPDATE eventSchedules SET lastRun = ? WHERE id = ?", () -> {}, date.toString(), schedule.id()))).join();
                schedules.put(schedule.id(), new Schedule(schedule.id(), schedule.type(), schedule.koth(), schedule.time(), schedule.seconds(), date));
                start(schedule);
            } catch (RuntimeException exception) {
                Main.getInstance().getLogger().log(Level.WARNING, "Could not run event schedule #" + schedule.id(), exception);
            }
        }
    }

    private void start(Schedule schedule) {
        if (schedule.type() == Type.DTC) {
            DTC dtc = DTC.get();
            if (dtc == null || dtc.isRunning()) {
                Main.getInstance().getLogger().info("Skipped DTC schedule #" + schedule.id() + ": already running or unavailable.");
                return;
            }
            dtc.start();
        } else if (schedule.type() == Type.CONQUEST) {
            Conquest conquest = Conquest.get();
            if (conquest == null || conquest.isRunning()) {
                Main.getInstance().getLogger().info("Skipped Conquest schedule #" + schedule.id() + ": already running or unavailable.");
                return;
            }
            conquest.start();
        } else {
            KOTH koth = KOTH.get(schedule.koth());
            if (koth == null || koth.isRunning() || !koth.isConfigured()) {
                Main.getInstance().getLogger().info("Skipped KOTH schedule #" + schedule.id() + ": already running or unavailable.");
                return;
            }
            koth.start(schedule.seconds());
        }
    }

    public void close() { task.cancel(); }
}
