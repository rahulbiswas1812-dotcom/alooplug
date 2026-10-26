package com.aloo.pinata.scheduler;

import com.aloo.pinata.AlooPinataParty;
import com.aloo.pinata.config.Settings;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

/**
 * Next-event time, persisted in data.yml so restarts behave predictably.
 * Only touches the disk when the schedule actually changes (a few times per event, never per hit).
 */
public final class ScheduleManager {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private final AlooPinataParty plugin;
    private final File file;
    private long next;

    public ScheduleManager(AlooPinataParty plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        next = y.getLong("next-event-epoch-ms", 0L);
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next-event-epoch-ms", next);
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not save data.yml", e);
        }
    }

    public long next() {
        return next;
    }

    /** Sanitises the stored time against the current settings (startup + reload). */
    public void recalc() {
        Settings s = plugin.settings();
        if (!s.scheduleEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        if (next <= 0) {
            next = now + s.intervalMs;
            save();
        } else if (next < now) {
            long delay = s.catchUpMissed ? s.catchUpDelaySeconds * 1000L : s.intervalMs;
            next = now + delay;
            plugin.getLogger().info("A scheduled Pinata Party was missed while the server was offline - "
                    + (s.catchUpMissed ? "running ONE catch-up event soon." : "rescheduled one interval from now."));
            save();
        } else if (next > now + s.intervalMs) {
            next = now + s.intervalMs; // interval was shortened
            save();
        }
    }

    public void afterEvent(boolean manual, long scheduledStartMs) {
        Settings s = plugin.settings();
        long now = System.currentTimeMillis();
        long guard = now + s.announceSeconds * 1000L + 60_000L;
        long n;
        if (!manual && scheduledStartMs > 0) {
            n = scheduledStartMs + s.intervalMs;
        } else {
            n = next;
        }
        if (n < guard) {
            n = now + s.intervalMs;
        }
        next = n;
        save();
    }

    public void pushBack() {
        next = System.currentTimeMillis() + plugin.settings().intervalMs;
        save();
    }

    public void setNextInMinutes(double minutes) {
        next = System.currentTimeMillis() + Math.round(minutes * 60_000.0);
        save();
    }

    public String format(long epochMs) {
        if (epochMs <= 0) {
            return "-";
        }
        ZonedDateTime t = Instant.ofEpochMilli(epochMs).atZone(plugin.settings().zone);
        return FORMAT.format(t);
    }
}
